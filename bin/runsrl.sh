#!/bin/bash

DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [ -z "$CLEARSRL_HOME" ]; then
    SOURCE="${BASH_SOURCE[0]}"
    CLEARSRL_HOME="$( dirname "$SOURCE" )"
    while [ -h "$SOURCE" ]
    do 
        SOURCE="$(readlink "$SOURCE")"
        [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE"
        CLEARSRL_HOME="$( cd -P "$( dirname "$SOURCE"  )" && pwd )"
    done
    export CLEARSRL_HOME="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
fi

#echo $CLEARSRL_HOME

CPATH=.
for i in $( ls $CLEARSRL_HOME/lib/*.jar );
do
   CPATH=$CPATH:$i
done

OPTS="-Xmx12g -XX:+UseConcMarkSweepGC"
#OPTS="-Xmx1g -XX:+UseConcMarkSweepGC"


java $OPTS -cp $CPATH clearsrl.RunSRL $@
