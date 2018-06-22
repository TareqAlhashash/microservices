package com.investorbook.memberservice.dao.entites;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.Email;

import org.hibernate.annotations.GenericGenerator;

@Entity
@Table(name = "members")
public class MemberEntity {

	@Id
	@GenericGenerator(name = "member_id", strategy = "com.investorbook.memberservice.dao.MemberIdGenerator")
	@GeneratedValue(generator = "member_id")
	@Column(name = "id")
	private String id;

	@Email
	@Column(unique = true)
	private String email;

	@Column(name = "first_name")
	private String firstName;

	@Column(name = "last_name")
	private String lastName;

	private String about;

	@OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "member")
	private AddressEntity address;

	@Column(name = "password_hash")
	private String passwordHash;

	@Column(name = "photo_url")
	private String photoUrl;

	public MemberEntity() {
		super();
	}

	public MemberEntity(@Email String email, String firstName, String lastName, String about, AddressEntity address,
			String passwordHash, String photoUrl) {
		super();
		this.email = email;
		this.firstName = firstName;
		this.lastName = lastName;
		this.about = about;
		this.address = address;
		this.passwordHash = passwordHash;
		this.photoUrl = photoUrl;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getId() {
		return id;
	}

	public String getAbout() {
		return about;
	}

	public void setAbout(String about) {
		this.about = about;
	}

	public AddressEntity getAddress() {
		return address;
	}

	public void setAddress(AddressEntity address) {
		this.address = address;
	}

	public String getPhotoUrl() {
		return photoUrl;
	}

	public void setPhotoUrl(String photoUrl) {
		this.photoUrl = photoUrl;
	}

}
