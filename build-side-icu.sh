#!/bin/sh
set -x
if [ ! -d ./tmp ];
then
    mkdir tmp || exit 1
    ( cd tmp && ../icu4c/source/configure ) || exit 1
fi

make -C tmp -j${CORES-1} all && make -C tmp/data icu4j-data-install ICU4J_ROOT=$(pwd)/icu4j && make -C tmp/test/testdata all icu4j-data-install  ICU4J_ROOT=$(pwd)/icu4j || exit 1
