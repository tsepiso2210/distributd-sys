package com.distributedemail.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * CampaignResponseDto - returned to the client after a campaign is created.
 *
 * The client uses this to poll the campaign status endpoint later.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignResponseDto {

    private Long id;
    private String name;
    private String subject;
    private int totalRecipients;
    private LocalDateTime createdAt;
    private String status;
    private String message;
}
