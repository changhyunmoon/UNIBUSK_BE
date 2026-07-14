package team.unibusk.backend.domain.performanceLocation.application;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import team.unibusk.backend.domain.performanceLocation.application.dto.PerformanceLocationImportRow;
import java.io.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PerformanceLocationExcelParserTest {
    private final PerformanceLocationExcelParser parser=new PerformanceLocationExcelParser();

    @Test void 필수_헤더와_행을_스트리밍으로_읽는다() throws Exception {
        byte[] excel=excel(new String[]{"name","address","operatorName","operatorPhoneNumber","latitude","longitude","imageFileName"},
                new String[]{"공연장","서울","기관","02-1234","37.5","127.0","hall.jpg"});
        List<PerformanceLocationImportRow> rows=new ArrayList<>();
        int count=parser.parse(new ByteArrayInputStream(excel),rows::add);
        assertThat(count).isEqualTo(1);assertThat(rows.get(0).name()).isEqualTo("공연장");assertThat(rows.get(0).latitude()).isEqualTo(37.5);
    }

    @Test void 필수_헤더가_없으면_파일을_거부한다() throws Exception {
        byte[] excel=excel(new String[]{"name","address"},new String[]{"공연장","서울"});
        assertThatThrownBy(()->parser.parse(new ByteArrayInputStream(excel),r->{})).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("필수 헤더");
    }

    private byte[] excel(String[] headers,String[] values) throws IOException {
        try(var wb=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
            var sheet=wb.createSheet();var header=sheet.createRow(0);for(int i=0;i<headers.length;i++)header.createCell(i).setCellValue(headers[i]);
            var row=sheet.createRow(1);for(int i=0;i<values.length;i++)row.createCell(i).setCellValue(values[i]);wb.write(out);return out.toByteArray();
        }
    }
}
