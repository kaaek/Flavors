package com.flavors.dto;

/** The creator a campaign form opens with, e.g. after "Start a campaign" on a search result. */
public record CampaignCreatorResponse(Long id, String displayName, String foodCategory, String location) {
}
