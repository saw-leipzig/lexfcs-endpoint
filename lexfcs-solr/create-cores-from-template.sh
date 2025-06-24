#!/bin/bash
# Create core configs from templates.

set -e

. _helpers.sh

# source: templates (base config and overrides)
TEMPLATE_DIR=template_datadir
# destination: solr data dir with cores
DATA_DIR=data
# core configuration template
TEMPLATE_CORE=_base
# solr group:user (chown)
GRPUSR=8983:8983

# find all cores
CORES=($(find $TEMPLATE_DIR/data/ -maxdepth 1 -mindepth 1 -type d -not -name "$TEMPLATE_CORE" -exec basename {} \;))
_head "Creating ${#CORES[@]} core configs ..."

# remove existing core configs
rm -rf $DATA_DIR

# just copy everything
cp -r $TEMPLATE_DIR $DATA_DIR
# and then remove the template core config
rm -r $DATA_DIR/data/$TEMPLATE_CORE
# and then build the core configs
for core in "${CORES[@]}"; do
    _info "- Create config for core '$core' ..."
    mkdir -p $DATA_DIR/data/$core
    # copy base configs
    cp -r $TEMPLATE_DIR/data/$TEMPLATE_CORE/. $DATA_DIR/data/$core
    # copy overrides
    cp -r $TEMPLATE_DIR/data/$core/. $DATA_DIR/data/$core
done

# adjust permissions
chown -R $GRPUSR data
