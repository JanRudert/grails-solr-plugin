//
// This script is executed by Grails after plugin was installed to project.
// This script is a Gant script so you can use all special variables provided
// by Gant (such as 'baseDir' which points on project base dir). You can
// use 'ant' to access a global instance of AntBuilder
//
// For example you can create directory under project tree:
//
//    ant.mkdir(dir:"${basedir}/grails-app/jobs")
//
def solrPlainConfDir = "${basedir}/grails-app/conf/solr/plain"
if(! new File(solrPlainConfDir)?.exists()) {
  Ant.mkdir(dir: solrPlainConfDir)
  Ant.copy(todir: solrPlainConfDir) {
    fileset(dir: "${pluginBasedir}/src/solr-local/solr/conf" )
  }  
}

def solrMultiCoreConfDir = "${basedir}/grails-app/conf/solr/multicore"
if(! new File(solrMultiCoreConfDir)?.exists()) {
  Ant.mkdir(dir: solrMultiCoreConfDir)
}