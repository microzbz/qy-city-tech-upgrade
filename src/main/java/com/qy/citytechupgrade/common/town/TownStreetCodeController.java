package com.qy.citytechupgrade.common.town;

import com.qy.citytechupgrade.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TownStreetCodeController {
    @GetMapping("/api/town-street-codes")
    public ApiResponse<List<TownStreetCode>> list() {
        return ApiResponse.success(TownStreetCodeCatalog.list());
    }
}
