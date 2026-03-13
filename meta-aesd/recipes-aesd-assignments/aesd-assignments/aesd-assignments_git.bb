# See https://git.yoctoproject.org/poky/tree/meta/files/common-licenses
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# Inherit module for kernel module build support and update-rc.d for init scripts
inherit module update-rc.d

SRC_URI = "git://git@github.com/cu-ecen-aeld/assignments-3-and-later-HaVanDuc2002;protocol=ssh;branch=main \
			file://aesd-char-driver"

PV = "1.0+git${SRCPV}"
SRCREV = "5760e789d9fa2ff7223be5596533dd241cbebdda"

# Source root: both server/ and aesd-char-driver/ live under WORKDIR/git
S = "${WORKDIR}/git"

# Kernel module is in the aesd-char-driver subdirectory
EXTRA_OEMAKE += "-C ${STAGING_KERNEL_DIR} M=${S}/aesd-char-driver"

# Pass linker flags needed by aesdsocket
TARGET_LDFLAGS += "-pthread -lrt"

# Capture LDFLAGS at parse time before the module bbclass clears it at task time.
# (module.bbclass sets os.environ['LDFLAGS']="" so the shell sees an empty LDFLAGS)
AESDSOCKET_LDFLAGS = "${LDFLAGS}"

# ─── Package split ───────────────────────────────────────────────────────────
# Split the driver init script into its own package so both init scripts
# can each be registered via update-rc.d with independent priorities.
PACKAGES =+ "${PN}-driver"

FILES:${PN}-driver  = "${sysconfdir}/init.d/aesd-char-driver"
FILES:${PN}        += "${bindir}/aesdsocket"
FILES:${PN}        += "${sysconfdir}/init.d/aesdsocket"

# Main package pulls in the driver sub-package
RDEPENDS:${PN} += "${PN}-driver"

# ─── init scripts ────────────────────────────────────────────────────────────
# aesd-char-driver starts first (S90) so /dev/aesdchar exists before aesdsocket
INITSCRIPT_PACKAGES = "${PN}-driver ${PN}"

INITSCRIPT_NAME:${PN}-driver   = "aesd-char-driver"
INITSCRIPT_PARAMS:${PN}-driver = "defaults 90 10"

INITSCRIPT_NAME:${PN}          = "aesdsocket"
INITSCRIPT_PARAMS:${PN}        = "defaults 92 20"

# ─────────────────────────────────────────────────────────────────────────────

do_configure () {
	:
}

do_compile () {
	# Build the aesdchar kernel module.
	# Use a subshell so unset doesn't pollute the environment for the server build.
	( unset CFLAGS CPPFLAGS CXXFLAGS LDFLAGS; oe_runmake ${EXTRA_OEMAKE} modules )

	# Build the aesdsocket server application.
	# Pass LDFLAGS explicitly because module.bbclass clears os.environ['LDFLAGS'].
	oe_runmake -C ${S}/server LDFLAGS="${AESDSOCKET_LDFLAGS}"
}

do_install () {
	# Install the aesdchar kernel module into the staging rootfs
	unset CFLAGS CPPFLAGS CXXFLAGS LDFLAGS
	oe_runmake ${EXTRA_OEMAKE} INSTALL_MOD_PATH="${D}" modules_install

	# Install the aesdsocket binary
	install -d ${D}${bindir}
	install -m 0755 ${S}/server/aesdsocket ${D}${bindir}/

	# Install both SysV init scripts
	install -d ${D}${sysconfdir}/init.d
	install -m 0755 ${S}/server/aesdsocket-start-stop ${D}${sysconfdir}/init.d/aesdsocket
	install -m 0755 ${WORKDIR}/aesd-char-driver ${D}${sysconfdir}/init.d/aesd-char-driver
}

RPROVIDES:${PN} += "kernel-module-aesdchar"