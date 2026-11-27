# Solr Tests

## Anatomy

- Each test case has its own folder, named something like `case000`.
- Each test case must contain:
  - `docs.xml`: the documents to be indexed in solr, the Lexical Entries; in Solr XML format
  - `resultids.txt`: the list of result IDs (one per line), corresponding to the expected search results, using the `xmlId` of the Lexial Entry
  - `solr.query`: the Solr search query. Note that it should be a block query search query.
- Each test may additionally contain:
  - `cql.query`: the search query for the FCS basic search using CQL
  - `lexcql.query`: the search query for the FCS lexical search using LexCQL
- NOTE that whitespace matters for the file `solr.query`, less so for `cql.query` and `lexcql.query`. So extra leading/tailing whitespaces, additional line breaks may cause errors when comparing expected query conversion results.

## Creating a new Test

- Create a new folder [here](src/test/resources/query_regressions/). Choose a meaningful name or something like `case###` (with `#` being a digit).
- Add the necessary data files in the newly created folder, see [the _anatomy_ section](#anatomy) for what is required.
- Create a new Java test class in [`src/test/java/de/saw_leipzig/textplus/webservices/fcs/lexfcs_solr_endpoint/query/regression/`](src/test/java/de/saw_leipzig/textplus/webservices/fcs/lexfcs_solr_endpoint/query/regression/). You can take a look at existing classes.
  - Given the test case name `case123`, name your class `SolrQueryRegressionCase123Test` and file `SolrQueryRegressionCase123Test.java`.
  - **IMPORTANT**: update the `getCaseName` method that is used to associate the test with the test data files you created before, so `"case123"`.
  - Use `usesSolrData` method to control whether Solr querying is performed or just (lex)cql query conversion to solr queries. Please add either a LexCQL or CQL query, otherwise the whole test case makes no sense.
- Only tests that have a Java class will be run!

## Run tests

```bash
mvn clean test
```

Run a subset of tests via

```bash
# single test file
mvn test -Dtest=SolrQueryRegressionCase101LexCQLEqualityTest
# multiple as comma-separated list
mvn test -Dtest=SolrQueryRegressionCase000Test,SolrQueryRegressionCase001LexCQLTest,SolrQueryRegressionCase001CQLTest
# multiple via pattern expression
mvn test -Dtest=SolrQueryRegressionCase1*
```

You will notice that tests logs will not actually mention your Java classes (for reasons...) but only `SolrQueryRegressionsTestBase`.
Tests use the [`MDC` (Mapped Diagnostic Context) mechanism](https://logback.qos.ch/manual/mdc.html) to mention the test case name in the log output. See the square brackets after e.g., `...SolrQueryRegressionsTestBase.testSolrSearch [case000]: ...`.

Each test case runs 3 tests:

- `testSolrSearch`: using the solr query (`solr.query`) to check the expected result IDs (`resultids.txt`) against the indexed document (`docs.xml`) search result.
- `testCQLQueryConversion` (optional, if `cql.query` file exists) to check the query conversion of FCS CQL to Solr query
- `testLexCQLQueryConversion` (optional, if `lexcql.query` file exists) to check the query conversion of FCS LexCQL to Solr query

The order of test case execution may be non-deterministic. Not sure. Test classes inherit from `LuceneTestCase` which use the `com.carrotsearch.randomizedtesting.RandomizedRunner` that does stuff...

If the query conversion files are missing, the tests will be marked as skipped.

## Logs

In case more log output is required, change the [`src/test/resources/log4j2.xml`](src/test/resources/log4j2.xml) configuration. Most Solr loggers are set to `warn` (or `off` for loggers with deprecated/configuration warnings) to not output too much.

## Solr Core configuration

The Solr Core configuration (schema etc.) are located in [`src/test/resources/configsets/`](src/test/resources/configsets/).
Update if necessary!

## Existing Test Cases

- `case0**`: basic examples
- `case1**`: visual examples from LexFCS spec, appendix B2.1
  - `case16*`: "missing" spec examples for `<>` queries
  - `case17*`: query examples from LexFCS spec, appendix B2.1.2
- `case2**`: query conversion (without Solr querying)
- `case3**`: use case testing
  - `case30*`: `senseRef` querying
- `case9**`: real-world test cases

## Utility Scripts

* list LexCQL query from test cases:
  ```bash
  find case* -type f -name 'lexcql.query' | xargs -I{} sh -c 'echo -n "\e[1m$(dirname {})\e[0m\n  \e[3;36m"; cat {}; echo "\e[0m\n"
  ```
* more scripts can be found in: https://git.saw-leipzig.de/text-plus/FCS/lexfcs-testcases/
