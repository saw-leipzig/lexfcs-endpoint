# LexFCS endpoint example using Solr as search engine

This is a LexFCS endpoint implementation with example configuration and example data to quickly spin up a working deployment that can be easily adapted to custom data and environments.

The endpoint project consists of two parts:

- [`lexfcs-solr/`](lexfcs-solr/): the Solr search engine where data is indexed and searchable,
- [`lexfcs-solr-endpoint/`](lexfcs-solr-endpoint/): the actual LexFCS endpoint, a Java servlet for Jetty/Tomcat, forwarding FCS search requests to Solr and wrapping results appropriately.

The example deployment uses `docker compose` for easy setup and encapsulation. Please look at [`lexfcs-solr/README.md`](lexfcs-solr/README.md) and [`lexfcs-solr-endpoint/README.md`](lexfcs-solr-endpoint/README.md) for more details on configurations, data formats, and customization.

## Quickstart

1. Start up Solr with example data

   ```bash
   cd lexfcs-solr/
   # NOTE: please update the credentials
   cp credentials.sh.template credentials.sh
   # start and run in background
   ./startup.sh
   ```

2. Start LexFCS endpoint

   ```bash
   cd lexfcs-solr-endpoint/
   # build endpoint
   docker compose build
   # NOTE: please update the credentials ...
   cp .env.template .env
   # start and run in background
   docker compose up -d
   ```

3. Test LexFCS endpoint

   Assuming default configuration, the endpoint should be live at: http://localhost:8189/

   ```bash
   # endpoint description
   curl 'http://localhost:8189/?x-fcs-endpoint-description=true&x-indent-response=2'

   # basic search
   curl 'http://localhost:8189/?query=*&x-indent-response=2'

   # basic search with more parameters
   # - query (with type (BASIC/CQL search)): ?query=* ?queryType=cql
   # - result set: ?startRecord=1 ?maximumRecords=10 (from record 1 to 10)
   # - fcs relevant: recordSchema=http%3A%2F%2Fclarin.eu%2Ffcs
   # - resource selection: ?x-fcs-context=test_wiktionary-en
   # - pretty printing: ?x-indent-response=2
   curl 'http://localhost:8189/dict?query=a&queryType=cql&startRecord=1&maximumRecords=10&recordSchema=http%3A%2F%2Fclarin.eu%2Ffcs%2Fresource&x-fcs-context=test_wiktionary-en&x-indent-response=2'

   # lexical search
   curl 'http://localhost:8189/?query=*&queryType=lex&x-indent-response=2'
   ```

4. Stop (optional)

   ```bash
   ( cd lexfcs-solr-endpoint/ ; docker compose down -v )
   ( cd lexfcs-solr/ ; docker compose down -v )
   ```

## Configurations

There are multiple configuration files, please look at [`lexfcs-solr/README.md`](lexfcs-solr/README.md) and [`lexfcs-solr-endpoint/README.md`](lexfcs-solr-endpoint/README.md).

For changing credentials (Solr), update the Solr configuration [`lexfcs-solr/credentials.sh`](lexfcs-solr/credentials.sh) (template: [`lexfcs-solr/credentials.sh.template`](lexfcs-solr/credentials.sh.template)) but also update in the FCS endpoint configurations [`lexfcs-solr-endpoint/.env`](lexfcs-solr-endpoint/.env) (template: [`lexfcs-solr-endpoint/.env.template`](lexfcs-solr-endpoint/.env.template)) to allow access.

Both Solr and the FCS endpoint share a network (see [`lexfcs-solr/docker-compose.override.yml`](lexfcs-solr/docker-compose.override.yml) and [`lexfcs-solr-endpoint/docker-compose.override.yml`](lexfcs-solr-endpoint/docker-compose.override.yml)), so there should not be any port conflicts when run with docker compose. But if there are changes, please update the Solr endpoint URL in [`lexfcs-solr-endpoint/.env`](lexfcs-solr-endpoint/.env) (environment variable `SOLR_URL`).
