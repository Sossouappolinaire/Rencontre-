#!/usr/bin/env bash
# Range les fichiers à plat (racine) dans la structure Android.
# Convention de noms :
#   *.java                 -> app/src/main/java/bj/rencontre/
#   AndroidManifest.xml    -> app/src/main/
#   layout_xxx.xml         -> app/src/main/res/layout/xxx.xml
#   values_xxx.xml         -> app/src/main/res/values/xxx.xml
#   drawable_xxx.xml       -> app/src/main/res/drawable/xxx.xml
#   menu_xxx.xml           -> app/src/main/res/menu/xxx.xml
#   app_build.gradle       -> app/build.gradle
set -e
SRC=app/src/main
mkdir -p $SRC/java/bj/rencontre $SRC/res/layout $SRC/res/values $SRC/res/drawable $SRC/res/menu

for f in *.java; do [ -e "$f" ] && cp "$f" $SRC/java/bj/rencontre/; done
[ -e AndroidManifest.xml ] && cp AndroidManifest.xml $SRC/
[ -e app_build.gradle ] && cp app_build.gradle app/build.gradle
[ -e app_proguard-rules.pro ] && cp app_proguard-rules.pro app/proguard-rules.pro

for f in layout_*.xml;   do [ -e "$f" ] && cp "$f" $SRC/res/layout/${f#layout_};   done
for f in values_*.xml;   do [ -e "$f" ] && cp "$f" $SRC/res/values/${f#values_};   done
for f in drawable_*.xml; do [ -e "$f" ] && cp "$f" $SRC/res/drawable/${f#drawable_}; done
for f in menu_*.xml;     do [ -e "$f" ] && cp "$f" $SRC/res/menu/${f#menu_};     done
echo "Structure Android prête."
