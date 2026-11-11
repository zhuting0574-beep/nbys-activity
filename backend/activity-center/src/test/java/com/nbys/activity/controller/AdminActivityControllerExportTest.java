package com.nbys.activity.controller;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminActivityControllerExportTest {
    private final AdminActivityController controller = new AdminActivityController(null, null, null, null);

    @Test
    void enrollmentWorkbookUsesYesterdayFormatAndKeepsUserFieldsAuditable() throws IOException {
        List<Map<String, Object>> rows = Arrays.asList(
                enrollment(44, "大刀", "大刀", "大刀", 1),
                enrollment(99, "新", "", "新", 3)
        );

        try (Workbook workbook = controller.buildEnrollmentWorkbook("宁波甬士烽火头灯更换 第一批", rows)) {
            Sheet sheet = workbook.getSheet("报名表");
            assertEquals("宁波甬士烽火头灯更换 第一批｜报名表", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("呼号不为空时使用呼号；呼号为空时使用用户名", sheet.getRow(2).getCell(1).getStringCellValue());
            assertEquals(2d, sheet.getRow(3).getCell(1).getNumericCellValue());
            assertEquals(4d, sheet.getRow(3).getCell(3).getNumericCellValue());

            assertEquals("序号", sheet.getRow(5).getCell(0).getStringCellValue());
            assertEquals("用户ID", sheet.getRow(5).getCell(1).getStringCellValue());
            assertEquals("用户名", sheet.getRow(5).getCell(2).getStringCellValue());
            assertEquals("呼号", sheet.getRow(5).getCell(3).getStringCellValue());
            assertEquals("显示名称", sheet.getRow(5).getCell(4).getStringCellValue());
            assertEquals("报名人数", sheet.getRow(5).getCell(5).getStringCellValue());

            assertEquals(44d, sheet.getRow(6).getCell(1).getNumericCellValue());
            assertEquals("大刀", sheet.getRow(6).getCell(2).getStringCellValue());
            assertEquals("大刀", sheet.getRow(6).getCell(3).getStringCellValue());
            assertEquals("大刀", sheet.getRow(6).getCell(4).getStringCellValue());
            assertEquals("", sheet.getRow(7).getCell(3).getStringCellValue());
            assertEquals("新", sheet.getRow(7).getCell(4).getStringCellValue());
            assertTrue(sheet.getPaneInformation().isFreezePane());
        }
    }

    @Test
    void enrollmentFilenameUsesActivityNameAndReplacesIllegalCharacters() {
        assertEquals("宁波甬士烽火头灯更换 第一批.xlsx",
                controller.enrollmentExportFilename("宁波甬士烽火头灯更换 第一批"));
        assertEquals("测试_活动______.xlsx", controller.enrollmentExportFilename("测试/活动:*?\"<>"));
        assertEquals("活动报名表.xlsx", controller.enrollmentExportFilename("..."));
    }

    @Test
    void syncingSquadsRemovesOutOfRangeAssignmentsAndCreatesMissingStructure() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AdminActivityController activityController = new AdminActivityController(jdbc, null, null, null);

        activityController.syncSquads(7, 1, 2);

        verify(jdbc).update("update enrollments set camp_no=null,squad_no=null,job=null,updated_at=now() " +
                "where activity_id=? and (camp_no>? or squad_no>?)", 7, 1, 2);
        verify(jdbc).update("delete from squad_settings where activity_id=? and (camp_no>? or squad_no>?)", 7, 1, 2);
        verify(jdbc).update("delete from camp_settings where activity_id=? and camp_no>?", 7, 1);
        verify(jdbc).update("insert ignore into camp_settings(activity_id,camp_no,updated_at) values(?,?,now())", 7, 1);
        verify(jdbc).update("insert ignore into squad_settings(activity_id,camp_no,squad_no,name,radio_channel,locked,updated_at) values(?,?,?,?,?,0,now())",
                7, 1, 1, "A队", "439.100");
        verify(jdbc).update("insert ignore into squad_settings(activity_id,camp_no,squad_no,name,radio_channel,locked,updated_at) values(?,?,?,?,?,0,now())",
                7, 1, 2, "B队", "439.200");
    }

    private Map<String, Object> enrollment(int userId, String username, String callsign, String displayName, int participantCount) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("user_id", userId);
        row.put("username", username);
        row.put("callsign", callsign);
        row.put("display_name", displayName);
        row.put("participant_count", participantCount);
        return row;
    }
}
