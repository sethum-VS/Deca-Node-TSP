package com.decanode.routing.infrastructure;

/**
 * DTO for a single stop input. Supports two modes:
 *  - Coordinate mode: lat + lng are provided
 *  - Address mode: address string is provided (will be geocoded)
 */
public class StopInput {

    private Double lat;
    private Double lng;
    private String address;

    public StopInput() {}

    /** Create a coordinate-based stop */
    public static StopInput ofCoordinate(double lat, double lng) {
        StopInput s = new StopInput();
        s.lat = lat;
        s.lng = lng;
        return s;
    }

    /** Create an address-based stop */
    public static StopInput ofAddress(String address) {
        StopInput s = new StopInput();
        s.address = address;
        return s;
    }

    /** Returns true if this stop has raw coordinates */
    public boolean hasCoordinates() {
        return lat != null && lng != null;
    }

    /** Returns true if this stop has an address that needs geocoding */
    public boolean hasAddress() {
        return address != null && !address.isBlank();
    }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
}
