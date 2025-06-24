#!/bin/bash
# Copy official "_default" core configs to new core in template_datadir/data/
# arguments are: corename

set -e

DOCKERIMAGE=solr:9.1
TEMPLATE_DIR=template_datadir/data
CORE=${1:-_default}

COREDIR="${TEMPLATE_DIR}/${CORE}"

if [[ -d "$COREDIR" ]]; then
    >&2 echo "Core ${CORE} already exists in ${TEMPLATE_DIR}!"
    exit 1
fi

echo "Create core in: $COREDIR"

# create "core.properties"
mkdir -p "${COREDIR}"
touch "${COREDIR}/core.properties"

# copy "_default/conf/" from official solr image
docker create --name solr_config_template $DOCKERIMAGE
docker cp solr_config_template:/opt/solr/server/solr/configsets/_default/. "${COREDIR}"
docker rm solr_config_template
