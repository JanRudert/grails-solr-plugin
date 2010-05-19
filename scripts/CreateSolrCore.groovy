import groovy.xml.MarkupBuilder

includeTargets << grailsScript("Init")
includeTargets << grailsScript("_GrailsArgParsing")

target(main: "Creates a new Solr core") {
    depends(parseArguments)

    def coreName = argsMap["params"][0]
  if (coreName) {
    File solrXml = ensureSolrXml()
    insertCoreIfNotExists(solrXml, coreName)
    createConfigFolderIfNotExists(coreName)
  } else {
    println("Usage 'grails create-solr-core <coreName>'")
  }

}

private File ensureSolrXml() {
  File solrXml = new File("${basedir}/grails-app/conf/solr/solr.xml")
  if (!solrXml.exists()) {
    solrXml.createNewFile()
  }
  if (!solrXml.size()) {
    solrXml.withWriter {Writer writer ->
      writer << '<?xml version="1.0" encoding="UTF-8"?>\n'
      MarkupBuilder xml = new MarkupBuilder(writer)

      xml.solr(persistent: true, sharedLib: "lib") {
        cores(adminPath: "/admin/cores")
      }
    }
  }
  return solrXml
}

private def insertCoreIfNotExists(File solrXml, coreName) {
  Node solr = new XmlParser().parse(solrXml)
  Node core = solr.cores[0].find {it.attribute('name') == coreName}
  if (!core) {
    new Node(solr.cores[0], 'core', [name: coreName, instanceDir: "multicore/$coreName"])
    solrXml.withPrintWriter {writer ->
      new XmlNodePrinter(writer).print(solr)
    }
  }
}


private def createConfigFolderIfNotExists(coreName) {
  File coreDirSource = new File("${basedir}/grails-app/conf/solr/multicore/$coreName")
  if (coreDirSource.exists() && !coreDirSource.isDirectory()) {
    throw new IllegalStateException("Found file with core name $coreDirSource.absolutePath")
  }

  if (!coreDirSource.exists()) {
    Ant.mkdir(dir: coreDirSource.absolutePath)
    Ant.copy(todir: coreDirSource.absolutePath) {
      fileset(dir: "${basedir}/grails-app/conf/solr/plain")
    }
  }
}

setDefaultTarget(main)
