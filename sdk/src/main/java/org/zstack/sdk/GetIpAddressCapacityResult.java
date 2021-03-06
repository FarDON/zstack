package org.zstack.sdk;



public class GetIpAddressCapacityResult {
    public long totalCapacity;
    public void setTotalCapacity(long totalCapacity) {
        this.totalCapacity = totalCapacity;
    }
    public long getTotalCapacity() {
        return this.totalCapacity;
    }

    public long availableCapacity;
    public void setAvailableCapacity(long availableCapacity) {
        this.availableCapacity = availableCapacity;
    }
    public long getAvailableCapacity() {
        return this.availableCapacity;
    }

    public long usedIpAddressNumber;
    public void setUsedIpAddressNumber(long usedIpAddressNumber) {
        this.usedIpAddressNumber = usedIpAddressNumber;
    }
    public long getUsedIpAddressNumber() {
        return this.usedIpAddressNumber;
    }

    public java.util.List capacityData;
    public void setCapacityData(java.util.List capacityData) {
        this.capacityData = capacityData;
    }
    public java.util.List getCapacityData() {
        return this.capacityData;
    }

    public java.lang.String resourceType;
    public void setResourceType(java.lang.String resourceType) {
        this.resourceType = resourceType;
    }
    public java.lang.String getResourceType() {
        return this.resourceType;
    }

}
