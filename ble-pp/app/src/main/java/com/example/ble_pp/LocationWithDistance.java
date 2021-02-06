package com.example.ble_pp;

public class LocationWithDistance {
    private double lattitude;
    private double longitude;
    private double distance;

    public LocationWithDistance(double lattitude, double longitude, double distance) {
        this.lattitude = lattitude;
        this.longitude = longitude;
        this.distance = distance;
    }

    public LocationWithDistance(double lattitude, double longitude) {
        this.lattitude = lattitude;
        this.longitude = longitude;
    }

    public double getLattitude() {
        return lattitude;
    }

    public void setLattitude(double lattitude) {
        this.lattitude = lattitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    @Override
    public String toString() {
        return "LocationWithDistance{" +
                "lattitude=" + lattitude +
                ", longitude=" + longitude +
                ", distance=" + distance +
                '}';
    }
}
