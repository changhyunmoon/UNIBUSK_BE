package team.unibusk.backend.domain.performanceLocation.application;

import com.github.pjfanning.xlsx.StreamingReader;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import team.unibusk.backend.domain.performanceLocation.application.dto.PerformanceLocationImportRow;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

@Component
public class PerformanceLocationExcelParser {
    private static final DataFormatter FORMATTER = new DataFormatter();
    private static final Set<String> REQUIRED = Set.of("name","address","operatorname","operatorphonenumber","latitude","longitude");

    public int parse(InputStream input, Consumer<PerformanceLocationImportRow> consumer) throws IOException {
        try (Workbook workbook = StreamingReader.builder().rowCacheSize(100).bufferSize(4096).open(input)) {
            Iterator<Row> rows = workbook.getSheetAt(0).iterator();
            if (!rows.hasNext()) throw new IllegalArgumentException("엑셀 헤더 행이 없습니다.");
            Map<String,Integer> header = readHeader(rows.next());
            if (!header.keySet().containsAll(REQUIRED)) throw new IllegalArgumentException("필수 헤더가 누락되었습니다: " + missing(header));
            int count=0;
            while (rows.hasNext()) {
                Row row=rows.next();
                if (isEmpty(row)) continue;
                count++;
                consumer.accept(new PerformanceLocationImportRow(row.getRowNum()+1,
                        value(row,header,"name"), value(row,header,"address"), value(row,header,"operatorname"),
                        value(row,header,"operatorphonenumber"), value(row,header,"availablehours"), value(row,header,"operatorurl"),
                        number(row,header,"latitude"), number(row,header,"longitude"), value(row,header,"imagefilename"),
                        value(row,header,"guide1"), value(row,header,"guide2"), value(row,header,"guide3"), null));
            }
            return count;
        }
    }
    private Map<String,Integer> readHeader(Row row) {
        Map<String,Integer> map=new HashMap<>();
        for(Cell cell:row) map.put(normalize(FORMATTER.formatCellValue(cell)),cell.getColumnIndex());
        return map;
    }
    private Set<String> missing(Map<String,Integer> h){ Set<String> m=new HashSet<>(REQUIRED);m.removeAll(h.keySet());return m; }
    private String value(Row row,Map<String,Integer> h,String key){ Integer i=h.get(key);return i==null?"":FORMATTER.formatCellValue(row.getCell(i)).trim(); }
    private Double number(Row row,Map<String,Integer> h,String key){ String v=value(row,h,key);if(v.isBlank())return null;try{return Double.valueOf(v.replace(",",""));}catch(NumberFormatException e){return null;} }
    private String normalize(String v){return v==null?"":v.replace(" ","").replace("_","").trim().toLowerCase();}
    private boolean isEmpty(Row row){for(Cell c:row)if(!FORMATTER.formatCellValue(c).isBlank())return false;return true;}
}
