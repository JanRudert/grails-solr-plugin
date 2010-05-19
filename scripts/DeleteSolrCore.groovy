includeTargets << grailsScript("Init")
includeTargets << grailsScript("_GrailsArgParsing")

def solrHome = "${grails.util.BuildSettingsHolder.getSettings().projectWorkDir}/solr-home"

target(main: "Deletes a solr core from the solr.xml and deletes the core's data and configuration") {
  depends(parseArguments)

  def coreName = argsMap["params"][0]
  if (coreName) {
    File solrXml = new File("${basedir}/grails-app/conf/solr/solr.xml")
    if (solrXml.exists()) {
      String toDelete = removeCoreFromSolrXmlAndFetchPath(solrXml, coreName)
      deleteCoreConfigDirectory(coreName)
      deleteCoreDirectory(solrHome, toDelete)
    }
  } else {
    println("Usage 'grails delete-solr-core <coreName>'")
  }


}

private def removeCoreFromSolrXmlAndFetchPath(File solrXml, coreName) {
  String toDelete
  Node solr = new XmlParser().parse(solrXml)
  Node core = solr.cores[0].find {it.attribute('name') == coreName}
  if (core) {
    toDelete = core.attribute('instanceDir')

    solr.cores[0].remove(core)
    solrXml.withPrintWriter {writer ->
      new XmlNodePrinter(writer).print(solr)
    }
  }
  return toDelete
}

private def deleteCoreConfigDirectory(coreName) {
  File coreConfigDir = new File("${basedir}/grails-app/conf/solr/multicore/$coreName")
  if (coreConfigDir.exists()) {
    Ant.delete(dir: coreConfigDir.absolutePath)
  }
}

private def deleteCoreDirectory(String solrHome, toDelete) {
  if (toDelete && new File("$solrHome/$toDelete").exists()) {
    Ant.delete(dir: "$solrHome/$toDelete")
  }
}

setDefaultTarget(main)
