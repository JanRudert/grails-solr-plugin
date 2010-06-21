package org.grails.solr

import gant.Gant

import org.apache.solr.client.solrj.SolrServerException

import org.apache.solr.client.solrj.response.SolrPingResponse

import org.codehaus.gant.GantBinding
import org.grails.solr.SolrService

/**
 * @author jrudert
 */
class SolrRunner {

  String solrPluginDir
  String solrHomeDir
  String solrPort
  String solrStopPort
  String solrConfigSourceDir

  SolrService solrService

  public void startSolr() {
    startSolrServer()
    waitForSolrStarted()
  }

  private def startSolrServer() {

    GantBinding binding = createGantBinding()
    Gant gant = new Gant(binding)
    gant.loadScript(new File("$solrPluginDir/scripts/StartSolr.groovy"))
    gant.processTargets()

  }

  private GantBinding createGantBinding() {
    GantBinding binding = new GantBinding()
    binding.setVariable("solrPluginDir", solrPluginDir)
    binding.setVariable("solrHomeDir", solrHomeDir)
    binding.setVariable("solrPort", solrPort)
    binding.setVariable("solrStopPort", solrStopPort)
    binding.setVariable("solrConfigSourceDir", solrConfigSourceDir)
    return binding
  }

  private def waitForSolrStarted() {
    long start = System.currentTimeMillis()
    int maxWaitMillis = 30000
    boolean up = false

    def cores = solrService.getCores()
    def testServer
    if (cores){
      testServer = solrService.getServer(cores[0].url)
    } else {
      testServer = solrService.getServer()
    }

    while (!up) {
      try {
        SolrPingResponse ping = testServer.ping()
        if(!ping) {
           throw new IllegalStateException("No ping response")
        }
        up = true
      } catch (SolrServerException se) {
        if (se.cause instanceof ConnectException) {
          if (System.currentTimeMillis() - start > maxWaitMillis) {
            break
          }
        } else {
          throw se
        }
      }
    }

    if (!up)
      throw new IllegalStateException("Solr did not start")
    
    println "Waited ${System.currentTimeMillis() - start} ms for solr to come up."
  }


  private def stopSolr() {
    GantBinding binding = createGantBinding()
    Gant gant = new Gant(binding)
    gant.loadScript(new File("$solrPluginDir/scripts/StopSolr.groovy"))
    gant.processTargets()

  }


}
