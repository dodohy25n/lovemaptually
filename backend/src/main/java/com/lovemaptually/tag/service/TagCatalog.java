package com.lovemaptually.tag.service;

import com.lovemaptually.ai.TagDefinition;
import com.lovemaptually.tag.entity.Tag;
import com.lovemaptually.tag.repository.TagRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 태그 사전 33행을 읽는 한 곳입니다. 사전은 시드로만 채우고 런타임에 늘리지 않습니다.
 */
@Component
public class TagCatalog {

    private final TagRepository tagRepository;

    public TagCatalog(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Transactional(readOnly = true)
    public List<Tag> all() {
        return tagRepository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public Map<String, Tag> byName() {
        return all().stream().collect(Collectors.toMap(Tag::getName, Function.identity()));
    }

    @Transactional(readOnly = true)
    public Map<Long, Tag> byId() {
        return all().stream().collect(Collectors.toMap(Tag::getId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public List<TagDefinition> definitions() {
        return all().stream()
                .map(tag -> new TagDefinition(tag.getName(), tag.getHighLabel(), tag.getLowLabel()))
                .toList();
    }
}
