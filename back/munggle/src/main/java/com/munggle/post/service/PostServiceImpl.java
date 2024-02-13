package com.munggle.post.service;

import com.munggle.alarm.service.AlarmService;
import com.munggle.domain.exception.PostNotFoundException;
import com.munggle.domain.exception.UserNotFoundException;
import com.munggle.domain.model.entity.*;
import com.munggle.follow.service.FollowService;
import com.munggle.follow.retpository.FollowRepository;
import com.munggle.image.dto.FileInfoDto;
import com.munggle.image.service.FileS3UploadService;
import com.munggle.post.dto.request.PostCreateDto;
import com.munggle.post.dto.response.PostDetailDto;
import com.munggle.post.dto.request.PostUpdateDto;
import com.munggle.post.mapper.PostMapper;
import com.munggle.post.repository.*;
import com.munggle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.munggle.domain.exception.ExceptionMessage.POST_NOT_FOUND;
import static com.munggle.domain.exception.ExceptionMessage.USER_NOT_FOUND;

@Service @Slf4j
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final FileS3UploadService fileS3UploadService;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostImageRepository postImageRepository;
    private final TagRepository tagRepository;
    private final PostTagRepository postTagRepository;
    private final PostListService postListService;
    private final PostLikeRespository postLikeRespository;
    private final ScrapRepository scrapRepository;
    private final FollowService followService;
    private final FollowRepository followRepository;
    private final AlarmService alarmService;

    /**
     * 게시글 상세보기 메소드
     *
     * @param postId
     * @param userId
     * @return
     */
    @Override
    public PostDetailDto getDetailPost(Long postId, Long userId) {
        Post post = postRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new PostNotFoundException(POST_NOT_FOUND));

        // 내 게시글인지 확인
        Boolean isMine = post.getUser().getId().equals(userId);

        // 좋아요 여부 확인
        PostLikeId postLikeId = PostMapper.toPostLikedIdEntity(userId, postId);
        Boolean isLiked = postLikeRespository.existsByPostLikeIdAndIsDeletedFalse(postLikeId);

        // 스크랩 여부 확인
        ScrapId scrapId = PostMapper.toScrapIdEntity(userId, postId);
        Boolean isScraped = scrapRepository.existsByScrapIdAndIsDeletedFalse(scrapId);

        // 팔로우 여부 확인
        Boolean isFollowed = followService.checkIsFollowed(userId, post.getUser().getId());

        // 해당 post의 해시태그 명만 list로 가져오기
        List<String> hashtags = new ArrayList<>();
        List<PostTag> postTags = post.getPostTagList();
        for (PostTag postTag : postTags) {
            if (postTag.getIsDeleted() == false) {
                hashtags.add(postTag.getTag().getTagNm());
                postListService.saveRecentTag(userId, postTag.getTag().getId()); // 큐레이팅을 위한 해시태그 수집
            }
        }

        return PostMapper.toPostDetailDto(post, hashtags, isMine, isLiked, isScraped, isFollowed);
    }

    /**
     * 게시글 생성 메소드
     *
     * @param postCreateDto
     */
    @Override
    @Transactional
    public Long insertPost(PostCreateDto postCreateDto) {

        Post newPost = PostMapper.toEntity(postCreateDto);
        Long userId = postCreateDto.getUserId();
        User user = userRepository.findByIdAndIsEnabledTrue(userId)
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        newPost.addUserToPost(user);

        // 게시글 영속화
        Long postId = postRepository.save(newPost).getId();

        // 해시태그 저장
        List<String> hashtags = postCreateDto.getHashtags();
        List<PostTag> postTags = new ArrayList<>();
        for (String hashtag : hashtags) {
            Tag tag = tagRepository.findByTagNm(hashtag)
                    .orElseGet(() -> tagRepository.save(PostMapper.toTagEntity(hashtag)));

            PostTagId newPostTagId = PostMapper.toPostTagIdEntity(postId, tag.getId());
            PostTag newPostTag = PostMapper.toPostTagEntity(newPostTagId, newPost, tag);
            newPostTag.markAsDeleted(false);
            postTags.add(newPostTag);

            postListService.saveRecentTag(userId, tag.getId()); // 큐레이팅을 위한 해시태그 수집
        }

        postTagRepository.saveAll(postTags); // 영속화

        List<User> followedUsers = followRepository.findByFollowToIdAndIsFollowedTrue(userId)
                .stream()
                .map(Follow::getFollowFrom)
                .collect(Collectors.toList());
        for (User follow : followedUsers) {
            alarmService.insertAlarm("POST", user, follow, postId);
        }

        return postId; //게시글 번호 반환
    }

    @Override
    @Transactional
    public void savePostImages(List<MultipartFile> images, Long postId, Long userId) {
        Post newPost = postRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new PostNotFoundException(POST_NOT_FOUND));

        // 이미지 저장
        if (images != null) {
            List<MultipartFile> files = images;
            String uploadPath = userId + "/" + postId + "/";
            List<FileInfoDto> fileInfoDtos = fileS3UploadService.uploadFlieList(uploadPath, files); //s3 저장소에 업로드

            for (FileInfoDto fileInfo : fileInfoDtos) { // db에 이미지 파일 정보 저장
                PostImage newImage = PostMapper.toPostImageEntity(fileInfo, newPost);
                postImageRepository.save(newImage);
            }
        } else {
            log.info("images : null");
        }
    }


    @Override
    @Transactional
    public void savePostImage(MultipartFile image, Long postId, Long userId) {
        Post newPost = postRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new PostNotFoundException(POST_NOT_FOUND));

        // 이미지 저장
        String uploadPath = userId + "/" + postId + "/";
        FileInfoDto fileInfo = fileS3UploadService.uploadFile(uploadPath, image); //s3 저장소에 업로드

        // db에 이미지 파일 정보 저장
        PostImage newImage = PostMapper.toPostImageEntity(fileInfo, newPost);
        postImageRepository.save(newImage);
    }

    /**
     * 게시글 수정 메소드
     *
     * 수정 가능 필드: title / content / isPrivate / image / hashtag
     * @param postUpdateDto
     */
    @Override
    @Transactional
    public Long updatePost(PostUpdateDto postUpdateDto) {

        String newTitle = postUpdateDto.getPostTitle();
        String newContent = postUpdateDto.getPostContent();
        Boolean newIsPrivate = postUpdateDto.getIsPrivate();

        Post updatePost = postRepository.findByIdAndIsDeletedFalse(postUpdateDto.getPostId())
                .orElseThrow(() -> new PostNotFoundException(POST_NOT_FOUND));
        updatePost.updatePost(newTitle, newContent, newIsPrivate);

        // 기존 post image 삭제
        Long userId = updatePost.getUser().getId();
        String uploadPath = userId + "/" + updatePost.getId() + "/";
        fileS3UploadService.removeFolderFiles(uploadPath); // s3 저장소에 올라간 기존 파일 삭제

        postImageRepository.deleteByPostId(updatePost.getId()); // db에서 데이터 삭제

        // 기존 해시태그 삭제
        List<PostTag> deleteTags = postTagRepository.findAllByPost(updatePost);  // db에서 isDeleted true로 변경
        for (PostTag deleteTag : deleteTags) {
            deleteTag.markAsDeleted(true);
        }

        // 업데이트 된 해시태그 저장
        List<String> hashtags = postUpdateDto.getHashtags();
        List<PostTag> postTags = new ArrayList<>();
        for (String hashtag : hashtags) {
            Tag tag = tagRepository.findByTagNm(hashtag)
                    .orElseGet(() -> tagRepository.save(PostMapper.toTagEntity(hashtag)));

            PostTagId findId = PostMapper.toPostTagIdEntity(updatePost.getId(), tag.getId());
            PostTag updatePostTag = postTagRepository.findById(findId)
                    .orElse(PostMapper.toPostTagEntity(findId, updatePost, tag));
            updatePostTag.markAsDeleted(false); // isDeleted false로 변경
            postTags.add(updatePostTag);

            postListService.saveRecentTag(userId, tag.getId()); // 큐레이팅을 위한 해시태그 수집
        }
        postTagRepository.saveAll(postTags); // 영속화

        Long postId = postRepository.save(updatePost).getId(); //게시글 영속화
        
        // postId 반환
        return postId;
    }

    /**
     * 게시글 삭제 메소드
     *
     * @param postId
     */
    @Override
    @Transactional
    public void deletePost(Long postId) {
        Post post = postRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new PostNotFoundException(POST_NOT_FOUND));
        post.markAsDeleted();

        // post image 삭제
        List<PostImage> deleteImages = postImageRepository.findAllByPost(post);  // db에서 isDeleted true로 변경
        for (PostImage deleteImage : deleteImages) {
            deleteImage.markAsDeleted();
        }

        // post tag 삭제
        List<PostTag> deleteTags = postTagRepository.findAllByPost(post);  // db에서 isDeleted true로 변경
        for (PostTag deleteTag : deleteTags) {
            deleteTag.markAsDeleted(true);
        }
    }


    // ==== 좋아요 생성/삭제 ==== //
    @Override
    public void postLike(Long userId, Long postId) {
        PostLikeId postLikeId = PostMapper.toPostLikedIdEntity(userId, postId);
        User user = userRepository.findByIdAndIsEnabledTrue(userId)
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        Post post = postRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new PostNotFoundException(POST_NOT_FOUND));

        Optional<PostLike> postLike = postLikeRespository.findById(postLikeId);

        if (postLike.isPresent()) { // 좋아요가 이미 존재할 때
            Boolean isDeleted = postLike.get().isDeleted(); // isDeleted 여부에 따라
            postLike.get().markAsDeleted(!isDeleted); // update 후 저장
            post.calcLikeCount(isDeleted); // likeCnt 다시 저장

            postLikeRespository.save(postLike.get());
            postRepository.save(post);
        } else {
            post.calcLikeCount(true);

            postLikeRespository.save(PostMapper.toPostLikeEntity(postLikeId, user, post));
            postRepository.save(post);

            // 최초 1회 좋아요만 알림 생성
            alarmService.insertAlarm("LIKE", user, post.getUser(), postId);
        }

    }


    // ==== 스크랩 생성/삭제 ==== //
    @Override
    public void postScrap(Long userId, Long postId) {
        ScrapId scrapId = PostMapper.toScrapIdEntity(userId, postId);

        User user = userRepository.findByIdAndIsEnabledTrue(userId)
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
        Post post = postRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new PostNotFoundException(POST_NOT_FOUND));

        Optional<Scrap> scrap = scrapRepository.findById(scrapId);
        if (scrap.isPresent()) { // 스크랩 데이터가 이미 존재할 때
            Boolean isDeleted = scrap.get().isDeleted(); // isDeleted 여부에 따라
            scrap.get().markAsDeleted(!isDeleted); // update 후 저장

            scrapRepository.save(scrap.get());
        } else {
            scrapRepository.save(PostMapper.toScrapEntity(scrapId, user, post));
        }

    }


}
