package dev.redplate.ui

/**
 * The one device this app is built for: a Samsung Galaxy S24 Ultra in portrait, at its
 * default FHD+ resolution (1080 × 2340, 450 dpi). The usable height follows CLAUDE.md's
 * layout zones (0–370 dp read-only, 490–824 dp controls). Every screen preview uses this,
 * so a layout that only works on some other phone never looks fine in Studio.
 */
const val S24_ULTRA = "spec:width=384dp,height=824dp,dpi=450,orientation=portrait"
