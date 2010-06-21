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

package org.grails.solr

import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer
import org.apache.solr.client.solrj.impl.StreamingUpdateSolrServer

import org.codehaus.groovy.grails.commons.ConfigurationHolder
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.response.QueryResponse

class SolrService {

  boolean transactional = false
  def grailsApplication
  private def cores

  /**
  * Return a SolrServer
  * {@link http://lucene.apache.org/solr/api/org/apache/solr/client/solrj/SolrServer.html}
  */
  def getServer() {
    return createServer(defaultServerUrl)
  }

  private def createServer(String url) {
    def server = new CommonsHttpSolrServer(url)
    //server.setParser(new XMLResponseParser())
    return server
  }

  /**
   * Returns a SolrServer for the given url
   */
  def getServer(String url) {
    return createServer(url)
  }

  /**
   * Returns a server object for the given artefact type. Looks up the configuration.
   */
  def getServer(Class artefactType) {
    def rtn
    def url = getCoreUrl(artefactType)
    if (url) {
      rtn = getServer(url)
    } else {
      rtn = server
    }
    return rtn
  }

  /**
   * Returns the configured url for the core associated with the given artifact type,
   * null if not configured
   *
   * example configuration:
   *
    //for solr plugin: non-domain classes that should be indexed as well
    solr.additional=[ExampleCarDocument, ExampleCustomerDocument]

    //urls for solr server(different cores):
    solr {
      cores {
        vehicle {
          artefact = ExampleCarDocument
          url = "http://localhost:8983/solr/vehicle"
        }
        customer {
          artefact = ExampleCustomerDocument
          url = "http://localhost:8983/solr/customer"
        }
      }
    }
   *
   */
   String getCoreUrl(Class artefact) {
    return cores.find {core -> core.artefact == artefact}?.url
  }

  def getCores() {
    def rtn
    if (cores == null) {
      def coreConfigs = ConfigurationHolder.config.solr.cores
      if (coreConfigs) {
        cores = coreConfigs.collect {key, configObject ->
          new SolrCore(name: key, artefact: configObject.artefact, url: configObject.url)
        }
      } else {
        cores = [] //avoids parsing empty config on each call 
      }
    }
    return cores
  }

  /**
   * Returns the server url if configured, otherwise "http://localhost:8983/solr". Does not take core configurations
   * into account.
   */
  String getDefaultServerUrl() {
    return (grailsApplication.config.solr?.url) ? grailsApplication.config.solr.url : "http://localhost:8983/solr"
  }

  def getStreamingUpdateServer(queueSize=20, numThreads=3) {
    def server = new StreamingUpdateSolrServer( defaultServerUrl, queueSize, numThreads)
    return server     
  }

  /**
  * Execute a basic Solr query given a string formatted query
  *
  * @param query - the Solr query string
  * @return Map with 'resultList' - list of Maps representing results and 'queryResponse' - Solrj query result
  */
  def search(String query) {
    search( new SolrQuery( query ) )
  }


  /**
  * Executes a Solr query given a string formatted query and an url.
  */
  def search(String query, String url) {
    search( new SolrQuery( query), url)
  }

  /**
   * Given SolrQuery object and server url, execute Solr query.
   *
   * @param solrQuery - SolrQuery object representating the query  {@link http://lucene.apache.org/solr/api/org/apache/solr/client/solrj/SolrQuery.html}
   * @param url
   * @return SearchResult
   */
  def search(SolrQuery solrQuery, String url) {
    def server = getServer(url)
    return search(solrQuery, server);
  }

  /**
   * Given SolrQuery object and server, execute Solr query.
   * @param solrQuery
   * @param server
   * @return SearchResult
   */
  def search(SolrQuery solrQuery, server) {
    QueryResponse rsp = server.query(solrQuery);
    def results = collectResults(rsp)
    return new SearchResults(resultList: results, queryResponse: rsp)
  }

  /**
  * Given SolrQuery object, execute Solr query on default server url
  *
  * @param solrQuery - SolrQuery object representating the query {@link http://lucene.apache.org/solr/api/org/apache/solr/client/solrj/SolrQuery.html}
  * @return SearchResult
  */
  def search(SolrQuery solrQuery) {
    QueryResponse rsp = getServer().query( solrQuery );
    def results = collectResults(rsp)
    return new SearchResults(resultList: results, queryResponse: rsp);
  }

  /**
   * Collects the results of the query-response and creates a map.
   * @author Manuel Kasiske
   * @param QueryRepsponse: the response of the solr-query.
   */
  def collectResults(org.apache.solr.client.solrj.response.QueryResponse rsp) {
     def rtn = []
     rsp.results.each { doc ->
      def map = [:]
      doc.getFieldNames().each { it ->

        // add both the stripped field name and the actual solr field name to
        // the result map... little redundant but greater flexibilty in retrieving
        // results
        def strippedFieldName = SolrUtil.stripFieldName(it)
        map."${strippedFieldName}" = doc.getFieldValue(it)
        if(!map."${it}" && strippedFieldName != it)
          map."${it}" = doc.getFieldValue(it)
      }
      map.id = SolrUtil.parseSolrId(doc.getFieldValue("id"))?.id

      // Add the SolrDocument to the map as well
      // http://lucene.apache.org/solr/api/org/apache/solr/common/SolrDocument.html
      map.solrDocument = doc

      rtn << map
    }
    rtn
  }


    
  /**
  * Constitute SolrQuery for a haversine based spatial search. This method returns 
  * the SolrQuery object in case you need to manipulate it further (add facets, etc)
  *
  * @param query  a lucene formatted query to execute in addition to the location range query
  * @param lat    latitude in degrees
  * @param lng    longitude in degrees
  * @param range  the proximity range to filter results by (small the better performance). unit is miles unless the radius param is passed in which case it's whatever the unit of the radius is
  * @param start  result number of the first returned result - used in paging (optional, default: 0)
  * @param rows   number of results to include - aka page size (optional, default: 10)
  * @param sort   sort direction asc or desc (optional, default: asc)
  * @param funcQuery provide a function query to be summed with the hsin function (optional)
  * @param radius sphere radius for haversine algorigthm (optional, default: 3963.205 [earth radius in miles])
  * @param lat_field SOLR index field for latitude in radians (optional, default: latitude_d)
  * @param lng_field SOLR index field for latitude in radians (optional, default: longitude_d)
  * @return SolrQuery object representing this spatial query
  */  
  SolrQuery getSpatialQuery(query, lat, lng, range, start=0, rows=10, sort="asc", funcQuery="", radius=3963.205, lat_field="latitude_rad_d", lng_field="longitude_rad_d") {
    def lat_rad = Math.toRadians( lat )
    def lng_rad = Math.toRadians( lng )
    def hsin = "hsin(${lat_rad},${lng_rad},${lat_field},${lng_field},${radius})"
    def order = [asc: SolrQuery.ORDER.asc, desc: SolrQuery.ORDER.desc]
    
    if(funcQuery != "")
      funcQuery = "${funcQuery},"
    
    SolrQuery solrQuery = new SolrQuery( (query && query.trim()) ? "(${query}) AND _val_:\"sum(${funcQuery}${hsin})\"" : "_val_:\"sum(${funcQuery}${hsin})\"" )
    solrQuery.addFilterQuery("{!frange l=0 u=${range}}${hsin}")
    solrQuery.setStart(start)
    solrQuery.setRows(rows)
    solrQuery.addSortField("score", order[sort])
    return solrQuery
  }
  
  /**
  * Same as getSpatialQuery but executes query
  * @return Map with 'resultList' - list of Maps representing results and 'queryResponse' - Solrj query result
  */
  def querySpatial(query, lat, lng, range, start=0, rows=10, sort="asc", funcQuery="", radius=3963.205, lat_field="latitude_rad_d", lng_field="longitude_rad_d") {
    def solrQuery = getSpatialQuery(query, lat, lng, range, start, rows, sort, funcQuery, radius, lat_field, lng_field)
    return querySpatial(solrQuery, lat, lng, lat_field, lng_field)
  } 

  /**
  * Expected to be called after getSpatialQuery, assuming you need to further 
  * manipulate the SolrQuery object before executing the query. 
  * @return Map with 'resultList' - list of Maps representing results and 'queryResponse' - Solrj query result
  */  
  def querySpatial(SolrQuery solrQuery, lat, lng, lat_field="latitude_rad_d", lng_field="longitude_rad_d") {
    log.debug ("spatial query: ${solrQuery}")  
    def result = search(solrQuery)
    result.resultList.each {
      it.dist = Haversine.computeMi(lat, lng, Math.toDegrees(it."${SolrUtil.stripFieldName(lat_field)}"), Math.toDegrees(it."${SolrUtil.stripFieldName(lng_field)}"))
    }
    return result   
  }

  


}
