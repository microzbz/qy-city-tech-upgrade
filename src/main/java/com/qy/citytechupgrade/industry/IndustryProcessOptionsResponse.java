package com.qy.citytechupgrade.industry;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class IndustryProcessOptionsResponse {
    private List<String> processes;
    private boolean specialMode;
}
