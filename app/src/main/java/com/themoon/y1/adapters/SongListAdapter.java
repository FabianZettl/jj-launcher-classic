package com.themoon.y1.adapters;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;

import com.themoon.y1.MainActivity;
import com.themoon.y1.ThemeManager;
import com.themoon.y1.models.SongItem;

import java.util.List;

public class SongListAdapter extends BaseAdapter {
    private List<SongItem> items;

    public SongListAdapter(List<SongItem> items) {
        this.items = items;
    }

    @Override
    public int getCount() { return items.size(); }

    @Override
    public Object getItem(int position) { return items.get(position); }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final Button btn;

        if (convertView == null) {
            btn = MainActivity.instance.createListButton("");
            btn.setLayoutParams(new AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    AbsListView.LayoutParams.WRAP_CONTENT));
        } else {
            btn = (Button) convertView;
        }

        final SongItem song = items.get(position);
        String prefixIcon = MainActivity.instance.isAudiobookLibraryMode ? "🎧 " : "🎵 ";
        btn.setText(prefixIcon + song.title);

        // 🚀 [추가] 오디오북 모드라면 MainActivity의 함수를 빌려와 프로그레스 바를 그립니다!
        if (MainActivity.instance.isAudiobookLibraryMode) {
            int pos = MainActivity.instance.prefs.getInt("book_pos_" + song.file.getAbsolutePath(), 0);
            int dur = MainActivity.instance.prefs.getInt("book_dur_" + song.file.getAbsolutePath(), 0);
            if (pos > 0 && dur > 0) {
                MainActivity.instance.setupAudiobookProgress(btn, pos, dur); // 💡 새 엔진 호출로 교체!
            }
        }

        btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    btn.setBackground(MainActivity.instance.createButtonBackground(ThemeManager.getListButtonFocusedBg()));
                    btn.setTextColor(ThemeManager.getListButtonFocusedTextColor());
                    MainActivity.instance.showFastScrollLetter(song.title);
                } else {
                    btn.setBackground(MainActivity.instance.createButtonBackground(ThemeManager.getListButtonNormalBg()));
                    btn.setTextColor(ThemeManager.getTextColorPrimary());
                }
            }
        });
        btn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                MainActivity.instance.clickFeedback();
                MainActivity.instance.isLongPressConsumed = true; // 방어막 켜기

                // 🚀 [스마트 분기점] 현재 화면이 어디냐에 따라 롱클릭 기능을 다르게 작동시킵니다!
                if (MainActivity.instance.currentBrowserMode == 7) {
                    // 1. M3U 플레이리스트 내부라면 -> '삭제' 팝업 띄우기
                    MainActivity.instance.showRemoveFromPlaylistDialog(song.file);
                } else if (MainActivity.instance.currentBrowserMode == 5) {
                    // 2. 즐겨찾기(Favorites) 내부라면 -> '즐겨찾기 해제' 팝업 띄우기
                    MainActivity.instance.showRemoveFromFavoritesDialog(song.file);
                } else {
                    // 3. 그 외 일반 폴더/목록이라면 -> 기존처럼 '플레이리스트에 담기' 팝업 띄우기
                    MainActivity.instance.showAddToPlaylistDialog(song.file);
                }
                return true;
            }
        });
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.instance.clickFeedback();
                com.themoon.y1.managers.AudioPlayerManager.getInstance().playTrackList(MainActivity.instance.virtualSongList, position);

                // 🚀 [화면 전환 트리거] 노래가 재생되면 화면을 플레이어 화면(STATE_PLAYER = 3)으로 전환합니다.
                MainActivity.instance.changeScreen(3);
            }
        });

        return btn;
    }
}