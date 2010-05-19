package org.grails.solr.test
import org.grails.solr.Solr

/**
 * Dummy document for testing cores
 */
class ExampleCarDocument
{
    Long id
    @Solr(asText=true) String registrationNr // vehicle.registrationNr (license plate)
    @Solr(asText=true) String make  // vehicle.make
    @Solr(asText=true) String model // vehicle.model
    @Solr(asText=true) String internalNr //vehicle.internalNr
    @Solr(asText=true) String vehiclePresentation //vehicle.vehiclePresentation (presentation asText)
    @Solr(asText=true) String equipmentText //vehicle.equipmentText

    Integer modelYear //vehicle.modelYear
    int mileage //car.mileage
    Integer price //vehicle.price (only price.retailPrice)
    Long ownershipId //vehicle.vehicleOwnerShip <-> Department.vehicleOwnerShip (external/internal)

    Long purchaseDate //vehicle.stockDate (over 90 days in stock)
    String status  //vehicle.status (reserved or sold)
    String vehicleTaskType //vehicle.task (preparation for sale/ preparation for delivery)
    boolean isAdvertised//vehicle.isAdvertised ("you are currently not offering this vehicle in any marketplace")
    boolean hasPictures //vehicle.hasPictures (no pictures?)

    String toString()
    {
       "${this.class.name} : ${id}"
    }

}
