LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6"

inherit module update-rc.d

SRC_URI = "git://git@github.com/cu-ecen-aeld/assignment-7-HaVanDuc2002;protocol=ssh;branch=main \
           file://misc-modules \
           "

PV = "1.0+git${SRCPV}"
SRCREV = "e12398fd5b6759e214261646162a5e0558e85e2d"

S = "${WORKDIR}/git"

# Build only the misc-modules subdirectory; pass LDDINC explicitly so the misc-modules
# Makefile finds the ldd3 include/ headers (proc_ops_version.h, etc.)
EXTRA_OEMAKE += "-C ${STAGING_KERNEL_DIR} M=${S}/misc-modules LDDINC=${S}/include"

do_configure () {
	:
}

do_compile () {
	unset CFLAGS CPPFLAGS CXXFLAGS LDFLAGS
	oe_runmake ${EXTRA_OEMAKE} modules
}

do_install () {
	unset CFLAGS CPPFLAGS CXXFLAGS LDFLAGS
	oe_runmake ${EXTRA_OEMAKE} INSTALL_MOD_PATH="${D}" modules_install

	# Install SysV init script
	install -d ${D}${sysconfdir}/init.d
	install -m 0755 ${WORKDIR}/misc-modules ${D}${sysconfdir}/init.d/misc-modules
}

FILES:${PN} += "${sysconfdir}/init.d/misc-modules"

INITSCRIPT_NAME = "misc-modules"
INITSCRIPT_PARAMS = "defaults 91 9"

RPROVIDES:${PN} += "kernel-module-faulty kernel-module-hello"
