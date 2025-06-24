#!/bin/bash
# Documentation of startup procedure using a clean installation

# load message helpers
. _helpers.sh

# ##########################################################################
# credentials
# ##########################################################################
# setup credentials (will be removed after data import)
SOLR_SETUP_USER=fcs_setup
SOLR_SETUP_PASS=QVVVKPl3orSBqgxIM1X5

# user (run) credentials (see `credentials.sh.template`)
. credentials.sh

# ##########################################################################
# Restart solr with updated core confs
# ##########################################################################
# Stop old Solr instance and cores
_head "Stopping old Solr instance and restarting with new cores..."
_run_cmd "docker compose down"

# Build cores with configs (remove existing and create new ones)
docker run -it --rm -v "$PWD:/work" -w "/work" bash -- ./create-cores-from-template.sh

# Startup
_run_cmd "docker compose up -d"

# Load data into cores
_head "Waiting for new cores coming up to import data..."
sleep 15

# ##########################################################################
# Import data
# ##########################################################################
# find all cores with data
IMPORTDATA_DIR=import_data
TEMPLATE_DIR=template_datadir
CORES=($(find $IMPORTDATA_DIR/ -maxdepth 1 -mindepth 1 -type d -exec basename {} \;))
_head "Found ${#CORES[@]} cores for data..."
for core in "${CORES[@]}"; do
    if [[ ! -f "$IMPORTDATA_DIR/$core/$core.xml" ]]; then
        _warn "No documents for core '$core' to import?"
        continue
    fi
    if [[ ! -d "$TEMPLATE_DIR/data/$core/" ]]; then
        _warn "No configuration for core '$core'?"
        continue
    fi
    _info "Import documents into core '$core'"
    _run_cmd "docker run --rm -v "$PWD/$IMPORTDATA_DIR:/import_data" --network=host solr:9.1 post -u $SOLR_SETUP_USER:$SOLR_SETUP_PASS -c $core /import_data/$core/$core.xml"
    status=$?
    if [[ $status -ne 0 ]]; then
        _warn "Failure to import core '$core' data! Exit status=$status. Aborting!"
        _info "Often times it is enough to just run the 'startup.sh' command again. The first time seems to fail after longer periods of runtime."
        exit 1
    fi
done

# ##########################################################################
# Downgrade auth
# ##########################################################################
_head "Update credentials for access..."
_run_cmd "docker run --rm --network=host alpine/curl --silent --user $SOLR_SETUP_USER:$SOLR_SETUP_PASS http://localhost:8983/solr/admin/authentication -H 'Content-type:application/json' -d '{"set-user": {'"$SOLR_USER"': '"$SOLR_PASS"'}}'"
_run_cmd "docker run --rm --network=host alpine/curl --silent --user $SOLR_SETUP_USER:$SOLR_SETUP_PASS http://localhost:8983/solr/admin/authorization -H 'Content-type:application/json' -d '{"set-permission": {"name": "read", "role": "read-only"}}'"
# can also set to only "read-only" (read) instead of with "admin" (security-edit) role for more secure use
_run_cmd "docker run --rm --network=host alpine/curl --silent --user $SOLR_SETUP_USER:$SOLR_SETUP_PASS http://localhost:8983/solr/admin/authorization -H 'Content-type:application/json' -d '{"set-user-role": {'"$SOLR_USER"': ["read-only", "admin"]}}'"
_run_cmd "docker run --rm --network=host alpine/curl --silent --user $SOLR_SETUP_USER:$SOLR_SETUP_PASS http://localhost:8983/solr/admin/authentication -H 'Content-type:application/json' -d '{"delete-user": ['"$SOLR_SETUP_USER"']}'"
