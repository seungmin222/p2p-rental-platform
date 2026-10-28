package com.team7.rental.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.member.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {}
