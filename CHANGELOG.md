# Changelog

## [2.0.0](https://github.com/saw-leipzig/lexfcs-endpoint/releases/tag/2.0.0) - 2026-11-27

### Solr

- Upgrade to Solr 10.1 (update base schema)
- Update example data (language code BCP47)
- `startup.sh`: Add support for multiple input files: `<core>.xml` + `*.solr.xml`
- `startup.sh`: Better import error handling, abort on first error; some command argument quoting

### Endpoint

- Update dependencies (Solr 10.1 support)
- Add LexFCS 1.0 support: https://zenodo.org/records/22791989
    - Add new LexFCS 1.0 capability (keep legacy Lex capability for compatibility)
    - Add extensive [test suite](https://git.saw-leipzig.de/text-plus/FCS/lexfcs-testcases/) to verify LexFCS 1.0 LexCQL support
    - Update and fix LexCQL conversion to match Solr query evaluation
    - Add support for `x-fcs-lex-fields` request parameter
- Add support for `RESOURCE_PREFIX` resource prefix
- Add check to verify Solr search cores exist
- Fix handling of excessive pagination parameters
- Make `dataview_lexhits` optional, and fall back to `dataview_hits`
- General code quality improvements

### General

- Move `docker-compose.override.yml` to `*.template`, allow better configurability without git conflicts
- Update documentation
- Update CFF file
- Bump version (pom.xml, CFF file)

## [1.1.0](https://github.com/saw-leipzig/lexfcs-endpoint/releases/tag/1.1.0) - 2026-02-03

- Update dependencies
- Support of ExampleQuery element in EndpointDescription (XSD, dependencies, example)
- Improve LexCQL to Solr query conversion for some string quoting cases
- Fix exmple data (Leaf URL)
- Increase verbosity of maven build for docker logs
- Update CFF file

## [1.0.0](https://github.com/saw-leipzig/lexfcs-endpoint/releases/tag/1.0.0) - 2025-07-09

- Add Solr XML format documentation for LexFCS
- More documentation, fixing docker-compose setup
- Auto-formatter (VSCode)
- Documentation improvements
- Change docker-compose to docker compose
- Set pom version 1.0.0
