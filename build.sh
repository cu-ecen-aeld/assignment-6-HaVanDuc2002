#!/bin/bash
# Script to build image for qemu.
# Author: Siddhant Jajoo.

git submodule init
git submodule sync
git submodule update

# local.conf won't exist until this step on first execution
source poky/oe-init-build-env

CONFLINE="MACHINE = \"qemuarm64\""

cat conf/local.conf | grep "${CONFLINE}" > /dev/null
local_conf_info=$?

if [ $local_conf_info -ne 0 ];then
	echo "Append ${CONFLINE} in the local.conf file"
	echo ${CONFLINE} >> conf/local.conf
	
else
	echo "${CONFLINE} already exists in the local.conf file"
fi


bitbake-layers show-layers | grep "meta-aesd" > /dev/null
layer_info=$?

if [ $layer_info -ne 0 ];then
	echo "Adding meta-aesd layer"
	bitbake-layers add-layer ../meta-aesd
else
	echo "meta-aesd layer already exists"
fi

set -e

# Remove corrupted sstate manifest files before building
find tmp/sstate-control/ -name "index-*" -size 0 -delete 2>/dev/null || true
find tmp/sstate-control/ -name "index-*" -exec grep -lP '[\x80-\xFF]' {} \; -delete 2>/dev/null || true

# Detect and clean recipes whose configure.ac was corrupted by a prior OOM kill
# (symptom: configure.ac exists but is empty or missing AC_INIT)
for workdir in tmp/work/x86_64-linux/libxfixes-native tmp/work/cortexa57-poky-linux/gcc; do
	configure_ac=$(find "$workdir" -maxdepth 4 -name "configure.ac" 2>/dev/null | head -1)
	if [ -n "$configure_ac" ] && ! grep -q 'AC_INIT' "$configure_ac" 2>/dev/null; then
		recipe=$(basename "$workdir")
		echo "Corrupted configure.ac detected in $recipe — running cleansstate"
		bitbake -c cleansstate "$recipe" || true
	fi
done

# Limit parallelism to avoid OOM on low-memory hosts (8GB RAM)
# Always enforce via sed so re-runs pick up changes
if ! grep -q 'BB_NUMBER_THREADS' conf/local.conf; then
	cat >> conf/local.conf << 'EOF'

# Limit parallel tasks and make jobs to reduce peak memory usage
BB_NUMBER_THREADS = "2"
PARALLEL_MAKE = "-j 2"
# Reduce GCC linker memory usage
PACKAGECONFIG:remove:pn-gcc = "lto"
EOF
else
	sed -i 's/BB_NUMBER_THREADS = "[0-9]*"/BB_NUMBER_THREADS = "2"/' conf/local.conf
	sed -i 's/PARALLEL_MAKE = "-j [0-9]*"/PARALLEL_MAKE = "-j 2"/' conf/local.conf
fi
echo "BB_NUMBER_THREADS and PARALLEL_MAKE set to 2"

bitbake core-image-aesd
