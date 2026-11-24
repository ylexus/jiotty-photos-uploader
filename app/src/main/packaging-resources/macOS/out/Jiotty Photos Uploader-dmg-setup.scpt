-- jpackage's own DMGsetup.scpt (JDK 25), except for how it finds the disk. On macOS 26, Finder's "disks whose URL = ..." finds nothing, and
-- Finder lists a newly mounted disk only after a few seconds; jpackage's script then fails, jpackage ignores that, and the DMG gets no
-- Applications link or window layout. So this script waits for the disk to appear and finds it by its position in Finder's list of disks,
-- and closes its own window rather than whichever window has the disk's name.
tell application "Finder"
  set theDisk to missing value
  repeat 60 times
    set diskUrls to URL of every disk
    repeat with i from 1 to count of diskUrls
      if item i of diskUrls is "DEPLOY_VOLUME_URL" then set theDisk to disk i
    end repeat
    if theDisk is not missing value then exit repeat
    delay 0.5
  end repeat
  if theDisk is missing value then error "Finder does not list the disk DEPLOY_VOLUME_URL"
  open theDisk

  set theWindow to container window of theDisk

  set current view of theWindow to icon view
  set toolbar visible of theWindow to false
  set statusbar visible of theWindow to false

  -- size of window should fit the size of background
  set the bounds of theWindow to {400, 100, 920, 440}

  set theViewOptions to a reference to the icon view options of theWindow
  set arrangement of theViewOptions to not arranged
  set icon size of theViewOptions to 128
  set background picture of theViewOptions to POSIX file "DEPLOY_BG_FILE"

  -- Create alias for install location
  do shell script "(cd 'DEPLOY_VOLUME_PATH' && ln -s 'DEPLOY_INSTALL_LOCATION' 'DEPLOY_INSTALL_LOCATION_DISPLAY_NAME')"

  set allTheFiles to the name of every item of theWindow
  set xpos to 120
  set ypos to 290
  set i to 1
  set j to 0
  repeat with theFile in allTheFiles
    set theFilePath to POSIX path of theFile
    set appFilePath to POSIX path of "/DEPLOY_TARGET"
    if theFilePath ends with "DEPLOY_INSTALL_LOCATION_DISPLAY_NAME" then
      -- Position install location for default install dir
      set position of item theFile of theWindow to {390, 130}
    else if theFilePath ends with "DEPLOY_INSTALL_LOCATION" then
      -- Position install location for custom install dir
      set position of item theFile of theWindow to {390, 130}
    else if theFilePath ends with appFilePath then
      -- Position application or runtime
      set position of item theFile of theWindow to {120, 130}
    else
      -- Position all other items in rows by 3 item
      set position of item theFile of theWindow to {xpos, ypos}
      set xpos to xpos + 135
      if (i mod 3) is equal to 0
        set i to 1
        set ypos to ypos + 150
        set xpos to 120
      else
        set i to i + 1
      end if
      set j to j + 1
    end if
  end repeat

  -- Reduce icon size to 96 if we have additional content
  if j is not equal to 0
    set icon size of theViewOptions to 96
  end if

  -- Resize window to fit 1 or 2 extra raws with additional content
  -- 6 additional content will be displayed to user without scrolling
  -- for anything more then 6 scrolling will be required
  if j is greater than 0 and j is less than or equal to 3
    set the bounds of theWindow to {400, 100, 920, 525}
  end if
  if j is greater than 3
    set the bounds of theWindow to {400, 100, 920, 673}
  end if

  update theDisk without registering applications
  delay 5
  -- closing the window makes Finder save the layout in .DS_Store; give it time to, as jpackage detaches the disk right after this script
  close theWindow
  delay 2
end tell
