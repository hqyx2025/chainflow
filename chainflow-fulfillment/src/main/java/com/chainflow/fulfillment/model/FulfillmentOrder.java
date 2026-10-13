package com.chainflow.fulfillment.model;

public record FulfillmentOrder(String fulfillmentId, String orderId, String carrierCode, String status, String createdAt) {}
