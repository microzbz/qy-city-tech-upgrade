package com.qy.citytechupgrade.industry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndustryServiceTest {
    @Mock
    private IndustryProcessMapRepository industryProcessMapRepository;

    @Mock
    private ProcessEquipmentMapRepository processEquipmentMapRepository;

    @InjectMocks
    private IndustryService industryService;

    @Test
    void createProcessMappingKeepsSeparatorsInsideBracketsInSpecialMode() {
        when(industryProcessMapRepository.save(any(IndustryProcessMap.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        IndustryProcessMappingUpsertRequest request = new IndustryProcessMappingUpsertRequest();
        request.setIndustryCode("384");
        request.setIndustryName("电池制造");
        request.setSpecialMode(true);
        request.setProcessNamesText("极板制造(涂板、固化、化成)、极群组装(包板、焊接)");

        IndustryProcessMap saved = industryService.createProcessMapping(request);

        assertThat(saved.getProcessNamesText())
            .isEqualTo("极板制造(涂板、固化、化成);极群组装(包板、焊接)");
    }

    @Test
    void listProcessOptionsKeepsSeparatorsInsideBracketsInSpecialMode() {
        IndustryProcessMap mapping = new IndustryProcessMap();
        mapping.setIndustryCode("384");
        mapping.setIndustryName("电池制造");
        mapping.setSpecialMode(true);
        mapping.setProcessNamesText("极板制造(涂板、固化、化成);极群组装(包板、焊接)");
        when(industryProcessMapRepository.findByIndustryCode("384"))
            .thenReturn(Optional.of(mapping));

        IndustryProcessOptionsResponse response = industryService.listProcessOptions("384");

        assertThat(response.isSpecialMode()).isTrue();
        assertThat(response.getProcesses())
            .containsExactly("极板制造(涂板、固化、化成)", "极群组装(包板、焊接)");
    }
}
