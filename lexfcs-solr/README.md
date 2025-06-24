# Solr instance for storing LexFCS data

This project contains a storage solution for LexFCS dictionaries based on [Apache Solr](https://solr.apache.org/). It represents each dictionary by its own Solr `core` and can be used as data backend of the corresponding LexFCS endpoint implementation ([`lexfcs-solr-endpoint`](../lexfcs-solr-endpoint/)).

This deployment includes an example resource `test_wiktionary-en` but allows operators to easily add [more Solr Cores](#new-cores) to offer more LexFCS resources for searching.

One Solr Core represents a single lexical resource. Each lexical resource contains one or more lexical entries that are the smallest unit in LexFCS results. Each lexical entry contains various values belonging to specific lexical field types (e.g., lemma or definition) with attributes for additional information. LexFCS queries in LexCQL will be translated by the LexFCS Solr Endpoint into [Hierarchical Solr queries](https://solr.apache.org/guide/solr/latest/query-guide/block-join-query-parser.html) that allow to search lexical entries with conditions on their values and attributes. For this a relatively generic but nested data model is necessary, described in [more detail below](#data-format).

- Requires `docker` and `docker compose`.
- Will use [**Solr 9.1**](https://solr.apache.org/guide/solr/9_1/index.html).

## Setup

The script `create-core-from-solr-defaults.sh` has been used to create a template Solr core configuration. It should not be necessary to use this script anymore except to create a new template in the future.
The Solr Core template is basically unchanged except the [`conf/managed-schema.xml`](template_datadir/data/_base/conf/managed-schema.xml), describing the necessary fields to work for the LexFCS endpoint (around lines 113 -- 146).

## Core Creation

The Solr Core template is stored in `template_datadir/data/_base/` and will be used for all Solr cores. New Core creation will use this `_base/` directory and copy all files and folders. Custom _overlays_ to overwrite files can be created for a core (e.g., [`test_wiktionary-en`](template_datadir/data/test_wiktionary-en/)) and will simply be copied over the base configuration. If no overrides are required, create an emty folder. Each folder will result in a Solr core.

See script [`create-cores-from-template.sh`](create-cores-from-template.sh) for details.

The script [`startup.sh`](startup.sh) is used to create and start a new Solr instance. It will also create Solr cores based on template configurations and insert data if required.

### Example Core

An example Solr Core is supplied with `test_wiktionary-en`.

Configuration files can be found in: [`template_datadir/data/test_wiktionary-en/`](template_datadir/data/test_wiktionary-en/) (empty as it will only use the [`_base` template](template_datadir/data/_base/)).

Initial data files can be found in: [`import_data/test_wiktionary-en/`](import_data/test_wiktionary-en/).
An XML file with the same names as the Solr core, e.g. [`test_wiktionary-en.xml`](import_data/test_wiktionary-en/test_wiktionary-en.xml) will automatically be loaded into the newly created Solr core.

The startup script [`startup.sh`](startup.sh) will set up an new Solr instance, create a new `test_wiktionary-en` core and fill it with the provided data.

### New Cores

Each core requires an identifier (also used in the LexFCS endpoint). This identifier (e.g., `<core>`) is used to create a configuration folder (may be empty) and initial core data (may be skipped if manually created).
The configuration folder MUST be created in [`template_datadir/data/<core>`](template_datadir/data/) while core data should be placed in [`import_data/<core>/<core>.xml`](import_data/).

To store empty folders in version control, you may want to create a `.gitkeep` or similar file, so folders will be tracked.

Afterwards, simply run the [`startup.sh`](startup.sh) script again to re-create a new Solr instance. Existing other Solr cores will be deleted and repopulated, so take care!

Steps:

1. Setup core configurations
   - Create folder in [`template_datadir/data`](template_datadir/data), e.g. `template_datadir/data/<core>`.
   - Add a `.gitkeep` if no overrides against [`_base`](template_datadir/data/_base) are used.
2. Add core data
   - Create folder in [`import_data`](import_data), e.g. `import_data/<core>`.
   - Add XML documents file, e.g. `import_data/<core>/<core>.xml`.
3. Restart Solr with [`startup.sh`](startup.sh) for changes to take effect

### Data Format

Core data will be loaded from a [Solr XML](https://solr.apache.org/guide/solr/9_1/indexing-guide/indexing-with-update-handlers.html#xml-formatted-index-updates) file on startup at `import_data/<core>/<core>.xml`.

The basic structure for adding lexical entries is the following: (excerpt from example file: [`import_data/test_wiktionary-en/test_wiktionary-en.xml`](import_data/test_wiktionary-en/test_wiktionary-en.xml))

```xml
<add>
   <!-- a lexical entry -->
   <doc>
      <!-- basic meta data and information for the lexical entry -->

      <!-- important for structure for Solr+LexFCS endpoint queries -->
      <field name="_type">entry</field>
      <!-- the entry persistent identifier
           (in FCS results as <ResourceFragment pid="..." />) -->
      <field name="xmlId">https://en.wiktionary.org/wiki/leaf#Noun</field>
      <!-- the (default) language for the whole lexial entry -->
      <field name="xmlLang">eng</field>
      <!-- the langingpage/url to the lexial entry (at the home institution)
           (in FCS results as <ResourceFragment ref="..." />) -->
      <field name="landingpage">https://en.wiktionary.org/wiki/leaf</field>
      <!-- response that will be returned for FCS BASIC search results for this lexical entry -->
      <field name="dataview_hits">leaf: 1. The usually green and flat organ that represents the most prominent feature of most vegetative plants. 2. A foliage leaf or any of the many and often considerably different structures it can specialise into. 3. Anything resembling the leaf of a plant.</field>
      <!-- response that will be returned for FCS LEX search results for this lexical entry as LexHITS Data View -->
      <field name="dataview_lexhits">&lt;Hit kind="lex-lemma"&gt;leaf&lt;/Hit&gt;: &lt;Hit kind="lex-definition"&gt;The usually green and flat organ that represents the most prominent feature of most vegetative plants.&lt;/Hit&gt;, &lt;Hit kind="lex-definition"&gt;A foliage leaf or any of the many and often considerably different structures it can specialise into.&lt;/Hit&gt;, &lt;Hit kind="lex-definition"&gt;Anything resembling the leaf of a plant.&lt;/Hit&gt;</field>

      <!-- the actual fields and values of the lexical entry
           - each value is nested as a single document to bundle the content and any attributes together
           - ordering does not matter, so fields can be mixed as well as attributes within the document
           - the value's field type is set in: <field name="_type">value_*</field> -->
      <field name="values">
         <!-- a single field value, for field type "entryId" with only the content string in the <field name="value"> -->
         <doc>
            <field name="_type">value_entryId</field>
            <field name="value">https://en.wiktionary.org/wiki/leaf#Noun</field>
         </doc>
         <!-- another field, for "lemma" field -->
         <doc>
            <field name="_type">value_lemma</field>
            <field name="value">leaf</field>
         </doc>
         <!-- more fields can follow ... -->

      </field>
   </doc>

   <!-- more lexical entries can follow ...-->
</add>
```

Using the [LexFCS v0.3 specification](https://zenodo.org/records/15706299), the following template can be used (lists all lexical field types and lexical value attributes):

```xml
<add>
   <!-- first document -->
   <doc>
      <field name="_type">entry</field>

      <!-- basic meta data and information for the lexical entry -->

      <!-- the entry persistent identifier
           (in FCS results as <ResourceFragment pid="..." />) -->
      <field name="xmlId"><!-- any PID whether URI/URN/..., prefixed etc. as long as unique for your resource --></field>
      <!-- the (default) language for the whole lexial entry -->
      <field name="xmlLang"><!-- use a single ISO 639-3 language code --></field>
      <!-- the langingpage/url to the lexial entry (at the home institution)
           (in FCS results as <ResourceFragment ref="..." />) -->
      <field name="landingpage"><!-- should be a URL --></field>
      <!-- response that will be returned for FCS BASIC search results for this lexical entry
           NOTE: that <Hit> marker might be placed automatically when the FCS query mapped to Solr query matches here -->
      <field name="dataview_hits"><!-- a plain-text unformatted string rendering your lexical entry --></field>
      <!-- response that will be returned for FCS LEX search results for this lexical entry as LexHITS Data View -->
      <field name="dataview_lexhits"><!-- a plain-text string with optional <Hit kind="lex-*>...</Hit> markern, see spec, needs to be properly escaped --></field>

      <!-- the actual fields and values of the lexical entry (will be used to generate the Lex Data View)
           - each value is nested as a single document to bundle the content and any attributes together
           - ordering does not matter, so fields can be mixed as well as attributes within the document
           - the value's field type is set in: <field name="_type">value_*</field>  -->
      <field name="values">
         <doc>
            <!-- required for Solr structuring,
                 - contents are in the form of "value_<field type>", e.g., "value_lemma" for "lemma" field type
                 - available field types are: antonym, baseform, case, citation, definition, entryId, etymology, frequency, gender, holonym, hypernym, hyponym, lemma, meronym, number, phonetic, pos, ref, related, segmentation, senseRef, sentiment, synonym, transcription, translation
                 - a "value_lemma" subdocument is generally required for each lexical entry -->
            <field name="_type">value_<!-- type --></field>

            <!-- the actual field value contents are stored here
                 NOTE: it might be empty if only attributes are used but it is BETTER to set any value here -->
            <field name="value"><!-- the lexical field value --></field>

            <!-- the following lexical field value attributes are possible
                 - each attribute must only appear once!
                 - all attributes are optional but some might be required/good practice for certain field types -->

            <!-- used for linking related lexical field values,
                 - @xml:id is the head
                 - @idRefs can list any head id to refer to it, the referred to id should exist! -->
            <field name="xmlId"><!-- any string--></field>
            <field name="idRefs"><!-- valid XML id --></field>

            <!-- the language of the entry -->
            <field name="xmlLang"><!-- use a single ISO 639-3 language code --></field>
            <!-- additional language information using a URI to specify the @xmlLang attribute -->
            <field name="langUri"><!-- --></field>
            <!-- can only be set to "true", to mark this field value as preferred compared to other values for the same lexical field type; if not "true" then please do not provide this field (with any other value) -->
            <field name="preferred"><!-- "true" --></field>
            <!-- unspecified reference, generally a URL -->
            <field name="ref"><!-- URL --></field>
            <!-- vocabulary reference -->
            <field name="vocabRef"><!-- URI --></field>
            <!-- vocabulary value reference -->
            <field name="vocabValueRef"><!-- URI --></field>
            <!-- a type to provide more information besides the field type of the lexical value -->
            <field name="type"><!-- a string --></field>
            <!-- for citation field types, the following attributes are possible -->
            <field name="source"><!-- source as string description, e.g. newspaper title --></field>
            <field name="sourceRef"><!-- source reference, URL --></field>
            <field name="date"><!-- date string --></field>
         </doc>

         <!-- more field values can follow
              - the lexical field types do not require any sort of grouping or ordering and can be mixed
              - each lexical field value is a separate document-->
      </field>
   </doc>

   <!-- more lexical entries as new documents <doc>...</doc> can follow ... -->
<add>
```

## Login

There exist two kinds of authentication settings. For **setup** an `fcs_setup` user (see credentials in [`startup.sh`](startup.sh) and [`security.json`](template_datadir/data/security.json)) is being used. After setup is finished, the credentials in `credentials.sh` are used to create a new **runtime** API user and the `fcs_setup` user will be deleted.

Changing the `fcs_setup` credentials should not be required as those are only used in the setup process. If you plan to do so, changes in the [`security.json`](template_datadir/data/security.json) might be required.

The `fcs_setup` user _(see variable `SOLR_SETUP_\*` in [`startup.sh`](startup.sh) and settings in [`security.json`](template_datadir/data/security.json))_ is required with `security-edit` privileges to load the data. The runtime user that will later be created allows running Solr with less permissions and is a bit more secure.

The "real" authentication information are stored in the `credentials.sh` files. To configure this, copy the [`credentials.sh.template`](credentials.sh.template) to `credentials.sh` and **update** user and password. This will then be used as public credentials for API/web access. The [`startup.sh`](startup.sh) script sets up the new users at the end.

## Startup and Updates

For startup and to restart the Solr instance, the [`startup.sh`](startup.sh) should be used. Since the `fcs_setup` user is being deleted after inital setup is finished, changes to core data, new cores, deletion of cores etc. requires to rerun the [`startup.sh`](startup.sh) script.

NOTE: that if you manually insert data into your cores, this will be lost after a re-initialization with [`startup.sh`](startup.sh)!

The [`startup.sh`](startup.sh) script requires docker privileges for updating and running the [`docker-compose` deployment](docker-compose.yml).

## Sample query

- `curl --user "USER:PASSWORD" "http://localhost:8983/solr/test_wiktionary-en/select?indent=true&q=*%3A*"`
