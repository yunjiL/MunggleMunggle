package com.munggle.member.service;

import com.munggle.domain.model.entity.Member;
import com.munggle.member.dto.MemberCreateDto;
import com.munggle.member.dto.MemberInfoDto;
import com.munggle.member.mapper.MemberMapper;
import com.munggle.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberServiceImpl implements MemberService{

    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return memberRepository.findByUsernameAndIsDeletedFalse(username)
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다."));
    }

    public MemberInfoDto findMemberById(Long id) {
        Member member = memberRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다."));
        return MemberMapper.toMemberInfoDto(member);
    }

    @Override
    @Transactional
    public void joinMember(MemberCreateDto memberCreateDto) {
        Member newMember = MemberMapper.toEntity(memberCreateDto);
        memberRepository.save(newMember);
    }

    @Override
    @Transactional
    public void updateNickname(Long id, String newNickname) {
        Member member = memberRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다."));
        member.changeNickname(newNickname);
    }
}
