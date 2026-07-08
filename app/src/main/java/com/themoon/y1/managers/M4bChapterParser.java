package com.themoon.y1.managers;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class M4bChapterParser {

    // 🚀 파싱된 챕터 정보를 (시간, 제목) 형태의 Map과 시간순 List로 묶어서 반환하기 위한 캡슐
    public static class ChapterResult {
        public TreeMap<Integer, String> chaptersMap = new TreeMap<>();
        public List<Integer> timestamps = new ArrayList<>();
    }

    public static ChapterResult extractChapters(File m4bFile) {
        ChapterResult result = new ChapterResult();
        if (m4bFile == null || !m4bFile.exists() || !m4bFile.getName().toLowerCase().endsWith(".m4b")) {
            return result;
        }

        try {
            RandomAccessFile raf = new RandomAccessFile(m4bFile, "r");
            long pos = 0;
            long length = raf.length();

            // 🚀 MP4(m4b) 뼈대(Atom)를 순차적으로 스캔합니다.
            while (pos < length) {
                raf.seek(pos);
                int size = raf.readInt();
                if (size < 8) break; // 에러 방지

                byte[] typeBytes = new byte[4];
                raf.read(typeBytes);
                String type = new String(typeBytes);

                // 폴더(Container) 박스인 경우 안으로 파고듭니다.
                if (type.equals("moov") || type.equals("udta")) {
                    pos += 8; // 헤더(8바이트)만 건너뛰고 내부 탐색 시작
                    continue;
                }

                // 🎯 대망의 챕터(chpl) 비밀의 방 발견!
                if (type.equals("chpl")) {
                    int version = raf.readUnsignedByte();
                    raf.skipBytes(3); // flags 건너뛰기
                    raf.skipBytes(1); // reserved 건너뛰기

                    int chapterCount = 0;
                    if (version == 1) {
                        chapterCount = raf.readInt();
                    } else {
                        chapterCount = raf.readUnsignedByte();
                    }

                    for (int i = 0; i < chapterCount; i++) {
                        // 1. 시작 시간 추출 (단위: 100나노초 -> 밀리초 변환)
                        long startTime100ns = raf.readLong();
                        int startTimeMs = (int) (startTime100ns / 10000);

                        // 2. 챕터 제목 추출
                        int titleLen = raf.readUnsignedByte();
                        byte[] titleBytes = new byte[titleLen];
                        raf.read(titleBytes);
                        String title = new String(titleBytes, "UTF-8");

                        // 3. 맵에 예쁜 책갈피 아이콘과 함께 담기!
                        result.chaptersMap.put(startTimeMs, "🔖 " + title);
                    }
                    break; // 챕터 추출 완료, 탐색 즉시 종료!
                }
                pos += size; // 원하는 박스가 아니면 크기만큼 무시하고 점프 (초고속 스킵)
            }
            raf.close();

            // 타임스탬프 리스트도 오름차순으로 완성
            result.timestamps.addAll(result.chaptersMap.keySet());

        } catch (Exception e) {
            e.printStackTrace();
        }



        return result;
    }
}