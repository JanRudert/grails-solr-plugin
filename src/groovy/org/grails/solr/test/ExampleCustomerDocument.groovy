package org.grails.solr.test

import org.grails.solr.Solr

/**
 * Dummy document for testing cores
 */
class ExampleCustomerDocument
{
  Long id
  @Solr(asText=true)String firstName //customer.firstName
  @Solr(asText=true)String lastName  //customer.lastName
  @Solr(asText=true)String companyName  //customer.company.ame
  @Solr(asText=true)String email     //customer.email
  @Solr(asText=true)String phone //customer.phoneCountryPrefix, customer.phoneAreaCode, customer.phone
  @Solr(asText=true)String fax //customer.faxCountryPrefix, customer.faxAreaCode, customer.fax
  @Solr(asText=true)String mobile // customer.mobilePhoneCountryPrefix, customer.mobilePhone
  @Solr(asText=true)String streetAndNumber //customer.address.streetName, customer.address.streetNr
  @Solr(asText=true)String zipCode //customer.address.zipCode
  @Solr(asText=true)String city //customoer.address.city

  @Solr(asText=true)String latestRegistrationNr // customer.vehicleOwnership.latestVehicleRegistrationNo (license plate)
  @Solr(asText=true)String latestVehicleMake // customer.vehicleOwnership.latestVehicleMake
  @Solr(asText=true)String latestVehicleModel // customer.vehicleOwnership.latestVehicleModel


   List departmentIds // customer.customerRelation.department.id
   List responsibleUserIds// customer.customerRelation.salesPerson

   String businessType //customer.businessType (commercial/private)
   String customerType //customer.customerType (existing/prospect)
   List customerTaskTypes // customer.customerRelation.taskType (contact follow up/sale follow up)
   List salesPersonInitials = new ArrayList<String>()// user.initials

  def indexSolrDepartmentIds(org.apache.solr.common.SolrInputDocument solrInputDocument)
  {
      departmentIds.each {
        solrInputDocument.addField("departmentIds", it)
      }
  }

  def indexSolrResponsibleUserIds(org.apache.solr.common.SolrInputDocument solrInputDocument)
  {
      responsibleUserIds.each {
        solrInputDocument.addField("responsibleUserIds", it)
      }
  }

    def indexSolrCustomerTaskTypes(org.apache.solr.common.SolrInputDocument solrInputDocument)
    {
        customerTaskTypes.each {
          solrInputDocument.addField("customerTaskTypes", it)
        }
    }

}
