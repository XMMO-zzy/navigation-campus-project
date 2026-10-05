package com.tonghang.campus.event.controller;

import com.tonghang.campus.common.api.Result;
import com.tonghang.campus.common.security.RequireRole;
import com.tonghang.campus.common.security.UserContext;
import com.tonghang.campus.event.service.CompetitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 竞赛成果提交接口（M5，P2）。
 *
 * <p>来源：《13-API接口文档》§3.5「PUT /competition-enrolls/{id}/result 提交成果（获奖/作品资料，P2）」。
 *
 * <p>路径为 {@code /competition-enrolls/{id}/result}（无 competitions 前缀），故单独成类。
 */
@Tag(name = "M5 竞赛成果")
@RestController
@RequestMapping("/competition-enrolls")
public class CompetitionEnrollController {

    private final CompetitionService competitionService;

    public CompetitionEnrollController(CompetitionService competitionService) {
        this.competitionService = competitionService;
    }

    /** PUT /competition-enrolls/{id}/result —— 提交成果（FR-M5-07，P2） */
    @Operation(summary = "提交竞赛成果（获奖/作品资料，P2）")
    @RequireRole(RequireRole.USER)
    @PutMapping("/{id}/result")
    public Result<Map<String, Object>> submitResult(@PathVariable("id") Long id,
                                                    @RequestBody Map<String, Object> body) {
        return Result.ok(competitionService.submitResult(UserContext.requireLogin(), id, body));
    }
}
