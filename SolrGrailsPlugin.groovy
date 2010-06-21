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

import org.codehaus.groovy.grails.commons.metaclass.*
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.apache.solr.client.solrj.impl.*
import org.apache.solr.common.*
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.client.solrj.SolrQuery

import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler
import org.codehaus.groovy.grails.commons.GrailsClassUtils

import org.grails.solr.SolrIndexListener
import org.grails.solr.Solr
import org.grails.solr.SolrUtil
import org.springframework.beans.BeanUtils
import java.beans.PropertyDescriptor

class SolrGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.1 > *"
    // the other plugins this plugin depends on
    def dependsOn = [domainClass: '1.1 > *'] //, hibernate: '1.1 > *']
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
      "grails-app/views/error.gsp",
      "grails-app/domain/**",
      "grails-app/conf/spring/resources.groovy",
      "grails-app/conf/Config.groovy",
      "grials-app/UrlMappings.groovy",
      "grails-app/Datasource.groovy",
      "src/groovy/org/grails/solr/test/**"
    ]

  //static loadAfter = ['hibernate']

    // TODO Fill in these fields
    def author = "Mike Brevoort"
    def authorEmail = "brevoortm@avalonconsult.com"
    def title = "Grails Solr Plugin"
    def description = '''\\
Provides search capabilities for Grails domain model and more using the excellent Solr 
open source search server through the SolrJ library.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/Solr+Plugin"

    def doWithSpring = {
//      GroovyClassLoader classLoader = new GroovyClassLoader(getClass().getClassLoader())
//      ConfigObject config
//      try {
//         config = new ConfigSlurper().parse(classLoader.loadClass('SolrGrailsPluginConfig'))
//
//         }
//      } catch (Exception e) {/* swallow and use default */}
      
    }

    def doWithApplicationContext = { applicationContext ->
  
      // add the event listeners for reindexing on change
      def listeners = applicationContext.sessionFactory.eventListeners
      def listener = new SolrIndexListener()

      ['postInsert', 'postUpdate', 'postDelete'].each({
         addEventTypeListener(listeners, listener, it)
      })
    }

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional)
    }

    def doWithDynamicMethods = { ctx ->

        def inputClasses = determineInputClasses(application)

        inputClasses.each {inputClazz ->

            // define indexSolr() method for all domain classes
            inputClazz.metaClass.indexSolr << {server = null ->
                def solrService = ctx.getBean("solrService");
                if (!server)
                    server = solrService.getServer(delegate.class)

                // TODO - is there a bette way to ignore built in parameters?

                // create a new solr document
                def doc = new SolrInputDocument();

                index(delegate, application, doc)

                server.add(doc)
                server.commit()

            }

            // add deleteSolr method to domain classes
            inputClazz.metaClass.deleteSolr << {->
                def solrService = ctx.getBean("solrService");
                def server = solrService.getServer(delegate.class)
                server.deleteByQuery("id:${delegate.class.name}-${delegate.id}");
                server.commit()
            }

            // add deleteSolr method to domain classes
            /*
            inputClazz.metaClass.addSolr << { ->
              def solrService = ctx.getBean("solrService");
              def server = solrService.getServer

              server.addBean( delegate );
              server.commit()
            }
            */

            // add solrId method to domain classes
            inputClazz.metaClass.solrId << {->
                def solrService = ctx.getBean("solrService");
                SolrUtil.getSolrId(delegate)
            }

            inputClazz.metaClass.'static'.solrFieldName << {name ->
                def delegateObject = delegate
                def prefix = ""
                def solrFieldName
                def clazz = (delegate.class.name == 'java.lang.Class') ? delegate : delegate.class
                def prop = clazz.declaredFields.find {field -> field.name == name}

                if (!prop && name.contains(".")) {
                    prefix = name[0..name.lastIndexOf('.')]
                    name = name.substring(name.lastIndexOf('.') + 1)
                    List splitName = name.split(/\./)
                    splitName.remove(splitName.size() - 1)
                    splitName.each {
                        //println "Before: ${delegateObject}   ${it}"
                        delegateObject = delegateObject."${it}"
                        //println "After ${delegateObject}"
                    }

                    prop = clazz.declaredFields.find {field -> field.name == name}
                }

                def typeMap = SolrUtil.typeMapping[(prop?.type)]
                solrFieldName = (typeMap) ? "${prefix}${name}${typeMap}" : "${prefix}${name}"

                // check for annotations
                if (prop?.isAnnotationPresent(Solr)) {
                    def anno = prop.getAnnotation(Solr)
                    if (anno.field())
                        solrFieldName = prop.getAnnotation(Solr).field()
                    else if (anno.asText())
                        solrFieldName = "${prefix}${name}_t"
                    else if (anno.ignore())
                            solrFieldName = null;
                }

                return solrFieldName
            }

            inputClazz.metaClass.'static'.searchSolr << {query ->
                def solrService = ctx.getBean("solrService");
                def server = solrService.getServer(inputClazz)
                def solrQuery = (query instanceof org.apache.solr.client.solrj.SolrQuery) ? query : new SolrQuery(query)
                def objType = (delegate.class.name == 'java.lang.Class') ? delegate.name : delegate.class.name
                solrQuery.addFilterQuery("${SolrUtil.TYPE_FIELD}:${objType}")
                //println solrQuery

                def result = solrService.search(solrQuery,server)

                // GIVING UP ON THE OBJECT RESULTS FOR THE TIME BEING
                //def objectList = []
                //
                //result.queryResponse.getResults().each {
                //  def resultAsObject = SolrUtil.resultAsObject(it)
                //  if(resultAsObject)
                //    objectList << resultAsObject
                //}
                //
                //result.objects = objectList

                return result
            }

        } //domainClass.each
    }//doWithDynamicMethods

    private def determineInputClasses(application) {
        def domainClasses = []
        application.domainClasses.each {
            if (GrailsClassUtils.getStaticPropertyValue(it.clazz, "enableSolrSearch")) {
                domainClasses << it.clazz
            }
        }
        def additionalClasses = application.config.solr?.additional
        return additionalClasses ? domainClasses + additionalClasses : domainClasses
    }

    def onChange = { event ->
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.

    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

  // copied from http://hartsock.blogspot.com/2008/04/inside-hibernate-events-and-audit.html
  private addEventTypeListener(listeners, listener, type) {
        def typeProperty = "${type}EventListeners"
        def typeListeners = listeners."${typeProperty}"

        def expandedTypeListeners = new Object[typeListeners.length + 1]
        System.arraycopy(typeListeners, 0, expandedTypeListeners, 0, typeListeners.length)
        expandedTypeListeners[-1] = listener

        listeners."${typeProperty}" = expandedTypeListeners
    }

  private index(indexedObject, application, doc, depth = 1, prefix = "") {
      def domainDesc = application.getArtefact(DomainClassArtefactHandler.TYPE, indexedObject.class.name)

      def clazz, properties
      (clazz, properties) = determineClassAndProperties (domainDesc, indexedObject)

      properties.each { prop ->

      //println "the type for ${it.name} is ${it.type}"
      // if the property is a closure, the type (by observation) is java.lang.Object
      // TODO: reconsider passing on all java.lang.Objects
      //println "${it.name} : ${it.type}"
      if(!SolrUtil.IGNORED_PROPS.contains(prop.name) && prop.type != java.lang.Object) { 
      
        // look to see if the property has a solrIndex override method
        def overrideMethodName = (prop.name?.length() > 1) ? "indexSolr${prop.name[0].toUpperCase()}${prop.name.substring(1)}" : ""
        def overrideMethod = indexedObject.metaClass.pickMethod(overrideMethodName.toString(),(Class []) [doc.class].toArray())
        if(overrideMethod != null) {
          overrideMethod.invoke(indexedObject, doc)
        } 
        else if(indexedObject."${prop.name}" != null) {
          def fieldName = indexedObject.solrFieldName(prop.name);
          
          // fieldName may be null if the ignore annotion is used, not the best way to handle but ok for now
          if(fieldName) {
            def docKey = prefix + fieldName                
            def docValue = indexedObject.getProperty(prop.name)
          
            // Removed because of issues with stale indexing when composed index changes
            // Recursive indexing of composition fields
            //if(GrailsClassUtils.getStaticPropertyValue(docValue.class, "enableSolrSearch") && depth < 3) {
            //  def innerDomainDesc = application.getArtefact(DomainClassArtefactHandler.TYPE, docValue.class.name)
            //  index(application, docValue, doc, ++depth, "${docKey}.")
            //} else {
            //  doc.addField(docKey, docValue)                  
            //}
          
            // instead of the composition logic above, if the class is a domain class
            // then set the value to the Solr Id
            // TODO - reconsider this indexing logic as a whole
            if(DomainClassArtefactHandler.isDomainClass(docValue.class))
              doc.addField(docKey, SolrUtil.getSolrId(docValue))
            else
              doc.addField(docKey, docValue)
            
            // if the annotation asTextAlso is true, then also index this field as a text type independant of how else it's
            // indexed. The best way to handle the need to do this would be the properly configure the schema.xml file but
            // for those not familiar with Solr this is an easy way to make sure the field is processed as text which should 
            // be the default search and processed with a WordDelimiterFilter   
          
            def clazzProp = clazz.declaredFields.find{ field -> field.name == prop.name}
            if(clazzProp.isAnnotationPresent(Solr) && clazzProp.getAnnotation(Solr).asTextAlso()) {
              doc.addField("${prefix}${prop.name}_t", docValue)     
            }
          }
            
          //println "Indexing: ${docKey} = ${docValue}"
        }               
      } // if ignored props
    } // domainDesc.getProperties().each

    // add a field to the index for the field ype
    doc.addField(prefix + SolrUtil.TYPE_FIELD, indexedObject.class.name)
    
    // add a field for the id which will be the classname dash id
    doc.addField("${prefix}id", "${indexedObject.class.name}-${indexedObject.id}")
    
    if(doc.getField(SolrUtil.TITLE_FIELD) == null) {
      def solrTitleMethod = indexedObject.metaClass.pickMethod("solrTitle")
      def solrTitle = (solrTitleMethod != null) ? solrTitleMethod.invoke(indexedObject) : indexedObject.toString()
      doc.addField(SolrUtil.TITLE_FIELD, solrTitle)     
    }   
  } // index

   private def determineClassAndProperties(domainDesc, indexedObject) {
      def clazz, properties

      if (domainDesc) {
          properties = domainDesc.properties
          clazz = (indexedObject.class.name == Class.name) ? indexedObject : indexedObject.class
      } else {
          clazz = indexedObject.class
          properties = []
          BeanUtils.getPropertyDescriptors(clazz).each {PropertyDescriptor desc ->
              properties << ["name": desc.name, "type": desc.propertyType]
          }
      }

      [clazz, properties]

  }

}
