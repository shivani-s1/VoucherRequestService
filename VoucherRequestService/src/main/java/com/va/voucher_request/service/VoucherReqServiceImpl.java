package com.va.voucher_request.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.stereotype.Service;

import com.va.voucher_request.client.VoucherClient;
import com.va.voucher_request.dto.Voucher;
import com.va.voucher_request.exceptions.NoCompletedVoucherRequestException;
import com.va.voucher_request.exceptions.NoVoucherPresentException;
import com.va.voucher_request.exceptions.NotFoundException;
import com.va.voucher_request.exceptions.ParticularVoucherIsAlreadyAssignedException;
import com.va.voucher_request.exceptions.ResourceAlreadyExistException;
import com.va.voucher_request.exceptions.ScoreNotValidException;
import com.va.voucher_request.exceptions.VoucherIsAlreadyAssignedException;
import com.va.voucher_request.exceptions.VoucherNotFoundException;
import com.va.voucher_request.model.VoucherRequest;
import com.va.voucher_request.model.VoucherRequestDto;
import com.va.voucher_request.repo.VoucherRequestRepository;

@Service
@EnableFeignClients(basePackages = "com.*")
public class VoucherReqServiceImpl implements VoucherReqService {

	 private final VoucherRequestRepository vrepo;
	    private final VoucherClient voucherClient;

	    @Autowired
	    public VoucherReqServiceImpl(VoucherRequestRepository vrepo, VoucherClient voucherClient) {
	        this.vrepo = vrepo;
	        this.voucherClient = voucherClient;
	    }
	    
	    // Method for requesting a voucher based on certain conditions
	@Override
	public VoucherRequest requestVoucher(VoucherRequestDto request)
			throws ScoreNotValidException, ResourceAlreadyExistException {
	    // Extract candidate email and exam name from the request
		String userEmail = request.getCandidateEmail();
		String examName = request.getCloudExam();
	    // Check if a voucher request already exists for the specified exam and candidate
		boolean examExists = vrepo.existsByCloudExamAndCandidateEmail(examName, userEmail);
	    // If a request already exists, throw an exception
		if (examExists) {
			throw new ResourceAlreadyExistException("You have already requested voucher for this particular exam");
		}
	    // Create a new VoucherRequest
		VoucherRequest vreq = new VoucherRequest();
	    // Check if the requested score is valid for issuing a voucher (score >= 80)
		if (request.getDoSelectScore() >= 80) {
	        // Generate a unique request ID using UUID
			String requestID = UUID.randomUUID().toString();
			vreq.setId(requestID);
			vreq.setCandidateName(request.getCandidateName());
			vreq.setCandidateEmail(request.getCandidateEmail());
			vreq.setCloudExam(request.getCloudExam());
			vreq.setCloudPlatform(request.getCloudPlatform());
			vreq.setDoSelectScore(request.getDoSelectScore());
			vreq.setDoSelectScoreImage(request.getDoSelectScoreImage());
			vreq.setPlannedExamDate(request.getPlannedExamDate());
			vreq.setExamResult("Pending");
	        // Save the VoucherRequest to the repository
			vrepo.save(vreq);
			return vreq;

		} else {
	        // If the requested score is not valid, throw an exception
			throw new ScoreNotValidException("doSelectScore should be >= 80 to issue a voucher.");
		}
	}

	@Override
	public List<VoucherRequest> getAllVouchersByCandidateEmail(String candidateEmail) throws NotFoundException {
	    // Retrieve voucher requests from the repository based on the candidate email
		List<VoucherRequest> vouchersByCandidate = vrepo.findByCandidateEmail(candidateEmail);
	    // If no vouchers are found for the candidate email, throw an exception
		if (vouchersByCandidate.isEmpty()) {
			throw new NotFoundException("No vouchers found for candidate email: " + candidateEmail);
		}
	    // Return the list of voucher requests associated with the specified candidate email
		return vouchersByCandidate;
	}

	@Override

	public VoucherRequest updateExamDate(String voucherCode, LocalDate newExamDate) throws NotFoundException {
	    // Retrieve the voucher request from the repository based on the voucher code
		VoucherRequest voucherRequest = vrepo.findByVoucherCode(voucherCode);
	    // If a voucher request is found, update the planned exam date
		if (voucherRequest != null) {
			voucherRequest.setPlannedExamDate(newExamDate);
			try {
	            // Save the updated voucher request to the repository
				vrepo.save(voucherRequest);
				return voucherRequest;
			} catch (Exception e) {
	            // Handle any exception that might occur during the save operation
				e.printStackTrace();
				throw new VoucherNotFoundException("Error saving VoucherRequest");
			}

		} else {
	        // If no voucher request is found for the voucher code, throw an exception
			throw new NotFoundException("No voucher found for voucher code: " + voucherCode);

		}

	}

	@Override

	public VoucherRequest updateExamResult(String voucherCode, String newExamResult) throws NotFoundException {
	    // Retrieve the voucher request from the repository based on the voucher code
		VoucherRequest voucherRequest = vrepo.findByVoucherCode(voucherCode);
	    // If a voucher request is found, update the exam result
		if (voucherRequest != null) {
			voucherRequest.setExamResult(newExamResult);
			try {
	            // Save the updated voucher request to the repository
				vrepo.save(voucherRequest);
				return voucherRequest;
			} catch (Exception e) {
	            // Handle any exception that might occur during the save operation
				e.printStackTrace();
				throw new VoucherNotFoundException("Error saving VoucherRequest");
			}

		} else {
	        // If no voucher request is found for the voucher code, throw an exception
			throw new NotFoundException("No voucher found for voucher code: " + voucherCode);

		}

	}

	@Override
	public VoucherRequest assignVoucher(String voucherId, String emailId, String voucherrequestId)
			throws VoucherNotFoundException, NotFoundException, VoucherIsAlreadyAssignedException,
			ParticularVoucherIsAlreadyAssignedException {
	    // Retrieve the voucher information from the external voucher service
		Voucher voucher = voucherClient.getVoucherById(voucherId).getBody();
	    // If the voucher is not found, throw an exception
		if (voucher == null) {
			throw new VoucherNotFoundException();
		}
	    // If the voucher is already assigned to a specific user, throw an exception
		if (voucher.getIssuedTo() != null) {
			throw new ParticularVoucherIsAlreadyAssignedException();
		}
	    // Retrieve the voucher request from the repository based on the voucher request ID
		Optional<VoucherRequest> voucherRequest = vrepo.findById(voucherrequestId);
	    // If the voucher request is not found, throw an exception
		if (voucherRequest.isEmpty()) {
			throw new NotFoundException("Voucher Request is Not Found");
		}

		
		VoucherRequest request = voucherRequest.get();
	    // If the voucher request already has a voucher assigned, throw an exception
		if (request.getVoucherCode() != null) {
			throw new VoucherIsAlreadyAssignedException();
		}
		request.setVoucherCode(voucher.getVoucherCode());
		request.setVoucherExpiryLocalDate(voucher.getExpiryDate());
		request.setVoucherIssueLocalDate(LocalDate.now());
	    // Update the voucher information in the external voucher service
		voucherClient.assignUserInVoucher(voucherId, emailId);
		return vrepo.save(request);

		
	}

	@Override
	public List<VoucherRequest> getAllVoucherRequest() throws VoucherNotFoundException {
	    // Retrieve all voucher requests from the repository
		List<VoucherRequest> allRequest = vrepo.findAll();
	    // If no voucher requests are found, throw an exception
		if (allRequest.isEmpty()) {
			throw new VoucherNotFoundException();
		}
	    // Return the list of all voucher requests in the system
		return allRequest;
	}

	@Override
	public List<VoucherRequest> getAllAssignedVoucherRequest() throws NoVoucherPresentException {
	    // Retrieve all voucher requests from the repository
		List<VoucherRequest> allrequest = vrepo.findAll();
	    // List to store voucher requests with assigned vouchers
		List<VoucherRequest> assignedvouchers = new ArrayList<>();
	    // If no voucher requests are found, throw an exception
		if (allrequest.isEmpty()) {
			throw new NoVoucherPresentException();
		}
	    // Iterate through all voucher requests and add those with assigned vouchers to the list
		for (VoucherRequest v : allrequest) {
			if (v.getVoucherCode() != null) {
				assignedvouchers.add(v);
			}
		}
	    // Return the list of all voucher requests with assigned vouchers in the system
		return assignedvouchers;
	}

	@Override
	public List<VoucherRequest> getAllNotAssignedVoucherRequest() throws NoVoucherPresentException {
	    // Retrieve all voucher requests from the repository
		List<VoucherRequest> allrequest = vrepo.findAll();
	    // List to store voucher requests without assigned vouchers
		List<VoucherRequest> notassignedvouchers = new ArrayList<>();
	    // If no voucher requests are found, throw an exception
		if (allrequest.isEmpty()) {
			throw new NoVoucherPresentException();
		}
	    // Iterate through all voucher requests and add those without assigned vouchers to the list
		for (VoucherRequest v : allrequest) {
			if (v.getVoucherCode() == null) {
				notassignedvouchers.add(v);
			}
		}
	    // Return the list of all voucher requests without assigned vouchers in the system
		return notassignedvouchers;
	}

	@Override
	public List<VoucherRequest> getAllCompletedVoucherRequest() throws NoCompletedVoucherRequestException {
	    // Retrieve all voucher requests from the repository
		List<VoucherRequest> allrequest = vrepo.findAll();
	    // List to store voucher requests with completed exams
		List<VoucherRequest> filteredList = new ArrayList<>();
	    // Iterate through all voucher requests and filter those with completed exams
		for(VoucherRequest v: allrequest) {
			if(!v.getExamResult().equalsIgnoreCase("pending")) {
				filteredList.add(v);
			}
		}
	    // If no voucher requests with completed exams are found, throw an exception
		if(filteredList.isEmpty()) {
			throw new NoCompletedVoucherRequestException();
		}
	    // Return the list of all voucher requests with completed exams in the system
		return filteredList;
	}

}
