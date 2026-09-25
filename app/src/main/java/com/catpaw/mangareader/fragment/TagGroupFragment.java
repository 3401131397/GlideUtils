package com.catpaw.mangareader.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.catpaw.mangareader.R;
import com.catpaw.mangareader.model.TagItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TagGroupFragment extends Fragment {

    private LinearLayout containerLayout;

    public TagGroupFragment() {
        super(R.layout.fragment_tag_group);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tag_group, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        containerLayout = view.findViewById(R.id.tag_container);
        buildTagGroups();
        displayTagGroups();
    }

    private void buildTagGroups() {
        // Empty placeholder - tag groups are hardcoded in displayTagGroups
    }

    private void displayTagGroups() {
        containerLayout.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());

        List<TagGroup> groups = getTagGroups();

        for (TagGroup group : groups) {
            View groupView = inflater.inflate(R.layout.item_tag_group, containerLayout, false);
            TextView groupTitle = groupView.findViewById(R.id.tv_group_title);
            com.google.android.flexbox.FlexboxLayout chipsContainer = groupView.findViewById(R.id.flexbox_tags);

            groupTitle.setText(group.title);

            for (String tagName : group.tags) {
                TextView chipView = (TextView) inflater.inflate(R.layout.item_tag_chip, chipsContainer, false);
                chipView.setText(tagName);
                chipView.setOnClickListener(v -> onTagClicked(tagName));
                chipsContainer.addView(chipView);
            }

            containerLayout.addView(groupView);
        }
    }

    private void onTagClicked(String tagName) {
        if (getActivity() instanceof OnTagSearchListener) {
            ((OnTagSearchListener) getActivity()).onTagSearch(tagName);
        }
    }

    private List<TagGroup> getTagGroups() {
        List<TagGroup> groups = new ArrayList<>();

        groups.add(new TagGroup("主题 A 漫", new String[]{
                "剧情向", "校园", "纯爱", "人妻", "师生", "近亲",
                "百合", "YAOI", "性转", "NTR", "伪娘", "痴女", "全彩", "女性向"
        }));

        groups.add(new TagGroup("角色 / 扮演", new String[]{
                "萝莉", "御姐", "熟女", "正太", "巨乳", "贫乳",
                "女王", "教师", "女僕", "护士", "泳装", "眼镜",
                "连裤袜", "其他制服", "兔女郎"
        }));

        groups.add(new TagGroup("特殊 PLAY", new String[]{
                "群交", "足交", "SM", "肛交", "阿黑颜", "药物", "扶他",
                "调教", "野外露出", "催眠", "自慰", "触手", "兽交"
        }));

        groups.add(new TagGroup("其他", new String[]{
                "CG 集", "重口", "猎奇", "非 H", "血腥暴力"
        }));

        return groups;
    }

    private static class TagGroup {
        String title;
        String[] tags;

        TagGroup(String title, String[] tags) {
            this.title = title;
            this.tags = tags;
        }
    }

    public interface OnTagSearchListener {
        void onTagSearch(String tagName);
    }
}
