# Privacy policy

DailyLog has no accounts, ads, analytics or crash reporting. It doesn't
collect anything about you, and nothing you write in it is sent to its
makers or anyone else.

It declares no Android permissions at all, and it contains no networking
code: there is nothing in the app that can reach the internet.

## Where your writing goes

Your log is a plain text file you create or pick yourself, wherever you
keep it. If you choose dated files instead, you pick a folder and the app
writes one file per day inside it, named from a template you set.

Either way the app reaches it through the `content://` URI Android gives it
when you choose that file or folder, and keeps the access with
`takePersistableUriPermission` so it can write again next time. It can
reach nothing else on your phone.

## What the app keeps

On your phone, and nowhere else:

- **Your shortcuts,** in a small database inside the app.
- **Your settings:** which file or folder you chose, whether you use dated
  files, the file-name template, text size, appearance, and where your
  cursor was in the log.

Uninstalling the app deletes both. Your log file is yours and stays where
you put it.

## Backing up and restoring shortcuts

Exporting shortcuts writes a JSON file where you choose; importing reads a
JSON file, or a legacy CSV one, that you pick. Those files go where you
send them, and the app doesn't upload them anywhere.

## Android's own backup

The app leaves Android's backup enabled (`android:allowBackup="true"`), so
if you have backup turned on, your shortcuts and settings can be included
in the backup your phone makes. That is between you, your phone and
whoever provides your backup; DailyLog plays no part in it and never sees
it. Your log file is backed up, or not, according to where you keep it.

## Contact

Questions: open an issue at https://github.com/Seafloor-Studio/dailylog/issues.
