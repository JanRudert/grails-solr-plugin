import gant.Gant
import org.apache.solr.client.solrj.SolrServerException
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.client.solrj.response.SolrPingResponse
import org.apache.solr.common.SolrDocument
import org.apache.solr.common.params.ModifiableSolrParams
import org.codehaus.gant.GantBinding
import org.codehaus.groovy.grails.commons.ConfigurationHolder
import org.grails.solr.SolrService
import org.grails.solr.test.ExampleCarDocument
import org.grails.solr.test.ExampleCustomerDocument

/**
 * @author: rudi
 * @date: 30.04.2010
 */
class SolrCoresIntegrationTests extends GroovyTestCase {

  SolrService solrService

  private static int numberOfExecutedTests = 0

  public void setUp() {
    ensureRunningSolr()
    clearOutIndex(customerCoreServer)
    clearOutIndex(vehicleCoreServer)
  }

  private def ensureRunningSolr() {
    if (numberOfExecutedTests == 0) {
      startSolrServer()
      waitForSolrStarted()
    }
  }


  private def startSolrServer() {

    GantBinding binding = new GantBinding()
    binding.setVariable("solrPluginDir", baseDir)
    binding.setVariable("solrHomeDir", "${grails.util.BuildSettingsHolder.getSettings().projectTargetDir}/solr-home")

    Gant gant = new Gant(binding)
    gant.loadScript(new File("$baseDir/scripts/StartSolr.groovy"))
    gant.processTargets()

  }

  private File getBaseDir() {
    def baseDir = grails.util.BuildSettingsHolder.getSettings().baseDir
    return baseDir
  }

  private def waitForSolrStarted() {
    long start = System.currentTimeMillis()
    int maxWaitMillis = 60000
    boolean up = false
    while (!up) {
      try {
        SolrPingResponse ping = customerCoreServer.ping()
        assertNotNull(ping)
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

    assertTrue("Solr did not start", up)
    println "Waited ${System.currentTimeMillis() - start} ms for solr to come up."
  }

  private def clearOutIndex(server) {
    server.deleteByQuery("*:*")
    server.commit()
    ensureEmptyIndex(server)
  }

  void ensureEmptyIndex(server) {
    ModifiableSolrParams params = new ModifiableSolrParams()
    params.add("q", "*:*")
    QueryResponse response = server.query(params)
    assertEquals(0, response.results.numFound)
  }


  public void tearDown() {
    stopSolrIfLastTest()
  }

  private def stopSolrIfLastTest() {
    if (++numberOfExecutedTests == numberOfTestMethods) {
      stopSolrServer()
    }
  }

  private static int getNumberOfTestMethods() {
    return SolrCoresIntegrationTests.metaClass.methods*.name.findAll {it.startsWith("test")}.size()
  }

  private def stopSolrServer() {
    GantBinding binding = new GantBinding()
    binding.setVariable("solrHomeDir", "${grails.util.BuildSettingsHolder.getSettings().projectTargetDir}/solr-home")

    Gant gant = new Gant(binding)
    gant.loadScript(new File("$baseDir/scripts/StopSolr.groovy"))
    gant.processTargets()

  }

  def getCustomerCoreServer() {
    solrService.getServer(ExampleCustomerDocument)
  }

  def getVehicleCoreServer() {
    solrService.getServer(ExampleCarDocument)
  }


  void testCoreConfiguration() {
    Map flatten = ConfigurationHolder.config.flatten()
    assertEquals(flatten."solr.cores.car.url", vehicleCoreServer.baseURL)
    assertEquals(flatten."solr.cores.customer.url", customerCoreServer.baseURL)
  }

  void testIndexAllFieldsOfExampleCarDocument() {
    ExampleCarDocument vid = new ExampleCarDocument()
    vid.id = 4711
    vid.registrationNr = "TZH 78-11"
    vid.make = "Trabant"
    vid.model = "601s de luxe"
    vid.internalNr = "0815"
    vid.vehiclePresentation = "The gourgeous car from Sachsenreing"
    vid.equipmentText = """Modor:
Lufdgegühlder Zweydagder mid Drehschieber-Einlassdeuerung

Vendile:
geene

Noggenwelle:
ooch geene

Zahnriemen:
ooch geener

Zindung:
Molodov Abreiszindung

Zindgerzen:
Blidzgov 175 hl UdSSR

Vergaser:
Einloch-Schlauchrisselvergaser Padschgi Bradislava

Lischdmaschine:
GOW Sonnenundergang Peging

Benzinbumbe:
ooch geene

Anlasser:
VEB Anlassergombinad Winderschreg
"""
    vid.modelYear = 1989
    vid.mileage = 120000
    vid.ownershipId = 2
    vid.purchaseDate = new Date().time
    vid.status = "Sold"
    vid.vehicleTaskType = "prepForDelivery"
    vid.isAdvertised = false
    vid.hasPictures = true

    vid.indexSolr()


    ModifiableSolrParams params = new ModifiableSolrParams()
    params.add("q", "*:*")
    QueryResponse response = vehicleCoreServer.query(params)

    assertEquals(1, response.results.numFound)
    SolrDocument doc = response.results[0]
    assertEquals("$ExampleCarDocument.name-$vid.id", doc.getFieldValue("id"))
    assertEquals(vid.registrationNr, doc.getFieldValue("registrationNr_t"))
    assertEquals(vid.make, doc.getFieldValue("make_t"))
    assertEquals(vid.model, doc.getFieldValue("model_t"))
    assertEquals(vid.internalNr, doc.getFieldValue("internalNr_t"))
    assertEquals(vid.vehiclePresentation, doc.getFieldValue("vehiclePresentation_t"))
    assertEquals(vid.equipmentText, doc.getFieldValue("equipmentText_t"))
    assertEquals(vid.modelYear, doc.getFieldValue("modelYear_i"))
    assertEquals(vid.mileage, doc.getFieldValue("mileage_i"))
    assertEquals(vid.ownershipId, doc.getFieldValue("ownershipId_l"))
    assertEquals(vid.purchaseDate, doc.getFieldValue("purchaseDate_l"))
    assertEquals(vid.status, doc.getFieldValue("status_s"))
    assertEquals(vid.vehicleTaskType, doc.getFieldValue("vehicleTaskType_s"))
    assertEquals(vid.isAdvertised, doc.getFieldValue("isAdvertised_b"))
    assertEquals(vid.hasPictures, doc.getFieldValue("hasPictures_b"))
  }


  void testIndexAllFieldsOfCustomerIndexDocument() {
    ExampleCustomerDocument cid = new ExampleCustomerDocument()
    cid.id = 4711
    cid.firstName = "Alice"
    cid.lastName = "Wonderland"
    cid.companyName = "FairyTale Inc"
    cid.email = "alice@bob.de"
    cid.phone = "110"
    cid.fax = "112"
    cid.mobile = "115"
    cid.streetAndNumber = "Castle Avenue 2"
    cid.zipCode = "ABC"
    cid.city = "Atlantis"
    cid.latestRegistrationNr = "0-1"
    cid.latestVehicleMake = "Kutsche"
    cid.latestVehicleModel = "de luxe"

    cid.departmentIds = [43, 564, 2]
    cid.responsibleUserIds = [3, 459, 2]
    cid.customerTaskTypes = ["PreSale", "PostSale"]

    cid.businessType = "Private"
    cid.customerType = "Subject"

    cid.indexSolr()
    
    ModifiableSolrParams params = new ModifiableSolrParams()
    params.add("q", "*:*")
    QueryResponse response = customerCoreServer.query(params)
    
    assertEquals(1, response.results.numFound)
    SolrDocument doc = response.results[0]
    assertEquals("$ExampleCustomerDocument.name-$cid.id", doc.getFieldValue("id"))
    assertEquals(cid.firstName, doc.getFieldValue("firstName_t"))
    assertEquals(cid.lastName, doc.getFieldValue("lastName_t"))
    assertEquals(cid.companyName, doc.getFieldValue("companyName_t"))
    assertEquals(cid.email, doc.getFieldValue("email_t"))
    assertEquals(cid.phone, doc.getFieldValue("phone_t"))
    assertEquals(cid.fax, doc.getFieldValue("fax_t"))
    assertEquals(cid.mobile, doc.getFieldValue("mobile_t"))
    assertEquals(cid.streetAndNumber, doc.getFieldValue("streetAndNumber_t"))
    assertEquals(cid.zipCode, doc.getFieldValue("zipCode_t"))
    assertEquals(cid.city, doc.getFieldValue("city_t"))
    assertEquals(cid.latestRegistrationNr, doc.getFieldValue("latestRegistrationNr_t"))
    assertEquals(cid.latestVehicleMake, doc.getFieldValue("latestVehicleMake_t"))
    assertEquals(cid.latestVehicleModel, doc.getFieldValue("latestVehicleModel_t"))

    assertEquals(cid.departmentIds, doc.getFieldValue("departmentIds"))
    assertEquals(cid.responsibleUserIds, doc.getFieldValue("responsibleUserIds"))
    assertEquals(cid.customerTaskTypes, doc.getFieldValue("customerTaskTypes"))

    assertEquals(cid.businessType, doc.getFieldValue("businessType_s"))
    assertEquals(cid.customerType, doc.getFieldValue("customerType_s"))

  }

}
