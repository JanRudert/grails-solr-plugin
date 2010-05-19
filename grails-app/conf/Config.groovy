import org.grails.solr.test.ExampleCarDocument
import org.grails.solr.test.ExampleCustomerDocument
// The following properties have been added by the Upgrade process...
grails.views.default.codec="none" // none, html, base64
grails.views.gsp.encoding="UTF-8"

log4j = {
    trace 'org.grails.solr'
}

solr {
    url = "http://localhost:8983/solr"

  //non-domain classes to be enhanced for solr search
  additional=[ExampleCarDocument, ExampleCustomerDocument]

  //urls for solr server(different cores):
  cores {
    car {
      artefact = ExampleCarDocument
      url = "http://localhost:8983/solr/car"
    }
    customer {
      artefact = ExampleCustomerDocument
      url = "http://localhost:8983/solr/customer"
    }
  }
}