package com.investorbook.memberservice.dao.entites;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.MapsId;
import javax.persistence.OneToOne;
import javax.persistence.Table;

@Entity
@Table(name = "address")
public class AddressEntity {

	@Id
	private String id;

	private String line1;

	private String line2;

	private String suburb;

	@Column(name = "address_state")
	private String state;

	@Column(name = "zip_code")
	private String zipCode;

	private String country;

	@OneToOne(fetch = FetchType.LAZY)
	@MapsId
	private MemberEntity member;

	public AddressEntity() {
		super();
	}

	public AddressEntity(String id, String line1, String line2, String suburb, String state, String zipCode, String country, MemberEntity member) {
		super();
		this.id = id;
		this.line1 = line1;
		this.line2 = line2;
		this.suburb = suburb;
		this.state = state;
		this.zipCode = zipCode;
		this.country = country;
		this.member = member;
	}

	public String getId() {
		return id;
	}


	public void setId(String id) {
		this.id = id;
	}

	
	public String getLine1() {
		return line1;
	}

	public void setLine1(String line1) {
		this.line1 = line1;
	}

	public String getLine2() {
		return line2;
	}

	public void setLine2(String line2) {
		this.line2 = line2;
	}

	public String getSuburb() {
		return suburb;
	}

	public void setSuburb(String suburb) {
		this.suburb = suburb;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getZipCode() {
		return zipCode;
	}

	public void setZipCode(String zipCode) {
		this.zipCode = zipCode;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}
	

	public MemberEntity getMember() {
		return member;
	}

	public void setMember(MemberEntity member) {
		this.member = member;
	}

}
