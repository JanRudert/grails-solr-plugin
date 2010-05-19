/*
* Copyright 2010 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
* ----------------------------------------------------------------------------
* Original Author: Mike Brevoort, http://mike.brevoort.com
* Project sponsored by:
*     Avalon Consulting LLC - http://avalonconsult.com
*     Patheos.com - http://patheos.com
* ----------------------------------------------------------------------------
*/

includeTool << gant.tools.Execute

ant.property(environment: 'env')
grailsHome = ant.antProject.properties.'env.GRAILS_HOME'

def pluginBasedir = "${solrPluginDir}"
def solrHome = binding.variables["solrHomeDir"] ? solrHomeDir : "${grails.util.BuildSettingsHolder.getSettings().projectWorkDir}/solr-home"
def solrStopPort = "8079"
def solrPort = "8983"
def solrHost = "localhost"

target ( startsolr: "Start Solr Jetty Instance") {
    depends("stopsolr")
    depends("init")

    File solrXml = fetchAndOverrideSolrXml(solrHome)
    def cores = ensuresCoresIfConfigured(solrXml, solrHome)

    if (!cores) {
      // overlay the schema.xml config file in the apps grails-app/conf/solr directory (and other conf files)
      ant.copy(todir: "${solrHome}/conf", failonerror: false) {
        fileset(dir: "${basedir}/grails-app/conf/solr/plain")
      }
    }


		// pause just for a bit more time to be sure Solr Stopped
		Thread.sleep(1000)

		// start it up
		ant.java ( jar:"${solrHome}/start.jar", dir: "${solrHome}", fork:true, spawn:true) {
            jvmarg(value:"-Dsolr.solr.home=${solrHome}")
			jvmarg(value:"-DSTOP.PORT=${solrStopPort}")
			jvmarg(value:"-DSTOP.KEY=secret")
			arg(line:"etc/jetty-logging.xml etc/jetty.xml")
		}
		
			
		println "Starting Solr - Solr HOME is ${solrHome}"
		println "-----------"
		println "Solr logs can be found here: ${solrHome}/logs"
		println "Console access: http://localhost:${solrPort}/solr/"
    if (cores) {
      println "Solr cores: $cores"
    }
		println "-----------"
}

private File fetchAndOverrideSolrXml(solrHome) {
  File solrXmlSource = new File("${basedir}/grails-app/conf/solr/solr.xml")
  File solrXmlTarget = new File("${solrHome}/solr.xml")
  if (solrXmlSource?.exists()) {
    ant.copy(file: solrXmlSource.absolutePath, toFile: solrXmlTarget.absolutePath, failonerror: false)
  } else {
    if (solrXmlTarget.exists()) {
      solrXmlTarget.delete()
    }
  }
  return solrXmlTarget
}

private def ensuresCoresIfConfigured(File solrXml, solrHome) {
  def cores = []

  if (solrXml.exists()) {
    Node solr = new XmlParser().parse(solrXml)
    if (solr.cores) {
      solr.cores[0].each {

        String coreDir = "$solrHome/${it.attribute('instanceDir')}"
        ensureCoreDirectory(coreDir)

        String coreName = it.attribute('name')
        ensureCoreConfiguration(coreDir, coreName)
        cores << coreName
      }
    }
  }
  return cores
}

private def ensureCoreConfiguration(String coreDir, String coreName) {
  ant.copy(todir: "$coreDir/conf", failonerror: false) {
    fileset(dir: "${basedir}/grails-app/conf/solr/multicore/$coreName")
  }
}

private def ensureCoreDirectory(String coreDir) {
  if (!new File(coreDir).exists()) {
    ant.mkdir(dir: "$coreDir/conf")
    ant.mkdir(dir: "$coreDir/data")
  }
}

setDefaultTarget ( "startsolr" )

target(checkport: "Test port for solr") {
  condition(property: "solr.not.running") {
    not {
      socket(server: solrHost, port: solrStopPort)      
    }
  }
}

target(stopsolr: "Stop Solr") {
  depends("checkport", "init")

	if ( !Boolean.valueOf(ant.project.properties.'solr.not.running') ) {
    println "Stopping Solr..."
  	java ( jar:"${solrHome}/start.jar", dir: "${solrHome}", fork:true) {
      jvmarg(value:"-DSTOP.PORT=${solrStopPort}")
  		jvmarg(value:"-DSTOP.KEY=secret")
  		arg(value: "--stop")
  	}
	}
 
}

target(init: "Create the solr-home directory") {
  // copy over the resources for solr home
	ant.mkdir(dir: "${solrHome}")
	ant.copy(todir:"${solrHome}") {
		fileset( dir: "${pluginBasedir}/src/solr-local")
	}
}
