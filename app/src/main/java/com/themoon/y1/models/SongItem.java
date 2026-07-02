package com.themoon.y1.models; // 본인의 패키지 경로에 맞게 유지해주세요!

import java.io.File;

public class SongItem {
    public File file;
    public String title;
    public String artist;
    public String album;

    // 🚀 [신규 추가] 연도와 장르를 기억할 공간 선언!
    public String year;
    public String genre;

    // 💡 기존 코드 호환성을 위한 기본 생성자 (M3U 등에서 오류가 나지 않게 방어해 줍니다)
    public SongItem(File file, String title, String artist, String album) {
        this.file = file;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.year = "Unknown Year";
        this.genre = "Unknown Genre";
    }

    // 🚀 [신규 엔진] 연도와 장르까지 꽉 채워서 담아주는 진화된 생성자 추가!
    public SongItem(File file, String title, String artist, String album, String year, String genre) {
        this.file = file;
        this.title = title;
        this.artist = artist;
        this.album = album;
        // 값이 비어있으면(null) 자동으로 'Unknown' 꼬리표를 달아줍니다.
        this.year = (year != null && !year.trim().isEmpty()) ? year : "Unknown Year";
        this.genre = (genre != null && !genre.trim().isEmpty()) ? genre : "Unknown Genre";
    }
}