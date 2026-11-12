# Notice

Pixel Launcher Evolved is an independent project. It is not affiliated with,
endorsed by, or sponsored by Google LLC. Android, Pixel, and Pixel Launcher are
trademarks of Google LLC, used here only to identify the software this module
extends.

The module reads drawables, colours, and strings from the installed launcher at
runtime so that injected views match their surroundings; it does not
redistribute any Google asset.

The application icon is original. It borrows the Pixel Launcher's visual idiom —
a circular badge under a long diagonal shadow — so the two sit together on a
home screen, but it contains none of Google's marks. The Pixel Launcher's own
icon is the Google "G", which is a Google trademark and is deliberately not
reproduced here.

## Third-party code

At a Glance's information cards, extra information and When to show are
rewritten from Smartspacer's built-in Targets, Complications and Requirements
(https://github.com/KieronQuinn/Smartspacer, GPL-3.0, Kieron Quinn); several
of its vector icons are copied, each marked in its file. This module is GPL-3.0
as well. Smartspacer's third-party add-ons are not supported: they only answer
Smartspacer's own package.

The module packages [DexKit](https://github.com/LuckyPray/DexKit) (LGPL-3.0),
with its native library `libdexkit.so`, and FlatBuffers for Java (Apache-2.0),
which DexKit uses to pass queries. DexKit is only loaded after a launcher update
has renamed or moved something the module looks up by name.
