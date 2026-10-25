# Biscuit Player artwork

`source.jpg` is the exact user-supplied biscuit/play-button JPEG, preserved byte
for byte. No generated replacement, recoloring, cropping, or redrawing is used.
`scripts/prepare_biscuit_branding.py` creates the Android resources by proportional
resizing and black padding. The separate splash composition adds BISCUIT PLAYER
below the artwork; launcher icons contain no text.

Original SHA-256: `69f85f0f0f1d6cc0d45724eeaceb443176e161ba5d4e6b84ce9ae030b26ec7ee`.
`launcher-mask-preview.png` illustrates three masks; it is not a device screenshot.

The adaptive foreground keeps the visible biscuit inside the 66dp safe circle
of its 108dp canvas. Black background and padding protect it under circle,
rounded-square and other standard adaptive masks. Legacy density-specific and
round resources, plus store/web PNGs, use the same source. No monochrome adaptive
override is supplied, so Biscuit does not explicitly replace the red play button
with themed colors. Launcher/OS automatic theming remains device dependent.

The splash uses one static 288dp composition with the image and caption inside
the 192dp safe circle, shared by AndroidX on API 26–30 and the Android system on
API 31+. There is no extra Activity, artificial wait or splash animation. Cold
launches use the system splash; warm launches may show it briefly and hot resumes
normally do not show it, according to Android's lifecycle.

Application/alias/task icons inherit the same manifest mipmap. Notification
small icons retain their existing resource identifier but now use a standard
white play silhouette, since Android notifications require a monochrome alpha
mask and cannot display this opaque color photo correctly.

Internal Theme.mpvex names remain for resource compatibility. Upstream licenses,
credits, package namespace and application IDs are unchanged.

Device validation: test fresh cold launch (force-stop), warm relaunch after Back,
hot return from Recents, light/dark device themes, circular and rounded-square
launcher masks, task icon, and transition into Cinematic Home. Resource/build
checks cannot substitute for these tests; no emulator is installed here.
