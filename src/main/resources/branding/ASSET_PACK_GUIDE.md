# ArtWallpaper Manager asset pack

Concept: a framed painting (sunset sky, hills, sun) with sparkles, on a violet-to-coral plate.

icons/     App icon. icon.ico (16-256, hand-tuned small sizes), icon.icns, icon.png, icon-1024.png,
           Tauri-style 32x32 / 128x128 / 128x128@2x, Microsoft Store/MSIX tiles (Square*Logo, StoreLogo, Wide310x150Logo)
tray/      tray.ico (16/20/24/32/48/64, colour), tray-mono-white / tray-mono-black (.ico + png) for dark/light taskbars
installer/ WiX banner (493x58) and dialog (493x312) BMPs, plus 164x314 / 55x58 wizard images (Inno Setup style)
branding/  logo.svg (vector master), GitHub social preview (1280x640), README banner (1600x420)

Usage notes
- Tauri: copy icons/ files into src-tauri/icons and reference icon.ico in tauri.conf.json (bundle.icon).
  Tray: use tray/tray.ico.
- WiX: <WixVariable Id="WixUIBannerBmp" Value="wix-banner-493x58.bmp" /> and
       <WixVariable Id="WixUIDialogBmp" Value="wix-dialog-493x312.bmp" />
- GitHub: upload social-preview-1280x640.png under Settings > Social preview; use the README banner at the top of README.md.
- Windows caches icons aggressively; if the old icon sticks, clear the icon cache or rename the exe once.
