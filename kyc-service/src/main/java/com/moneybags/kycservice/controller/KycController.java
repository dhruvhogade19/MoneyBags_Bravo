package com.moneybags.kycservice.controller;

import com.moneybags.kycservice.dto.request.CreateKycRequest;
import com.moneybags.kycservice.dto.request.DocumentVerificationRequest;
import com.moneybags.kycservice.dto.request.KycDecisionRequest;
import com.moneybags.kycservice.dto.response.KycDocumentResponse;
import com.moneybags.kycservice.dto.response.KycResponse;
import com.moneybags.kycservice.entity.KycDocument;
import com.moneybags.kycservice.enums.DocumentType;
import com.moneybags.kycservice.service.KycDocumentService;
import com.moneybags.kycservice.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/kycs")
@Tag(
        name = "KYC",
        description = "APIs for KYC creation, document management, verification, final decision and CIF synchronization"
)
public class KycController {

    private final KycService kycService;
    private final KycDocumentService kycDocumentService;

    public KycController(
            KycService kycService,
            KycDocumentService kycDocumentService
    ) {
        this.kycService = kycService;
        this.kycDocumentService = kycDocumentService;
    }

    // =========================================================
    // CREATE KYC
    // =========================================================

    @Operation(
            summary = "Create KYC",
            description = """
                    Creates a new KYC record using customer data received from the CIF service.

                    The newly created KYC starts with status PENDING.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "KYC created successfully",
                    content = @Content(
                            schema = @Schema(
                                    implementation = KycResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data"
            )
    })
    @PostMapping
    public ResponseEntity<KycResponse> createKyc(
            @Valid
            @RequestBody
            CreateKycRequest request
    ) {

        KycResponse response =
                kycService.createKyc(request);

        return ResponseEntity
                .status(201)
                .body(response);
    }

    // =========================================================
    // GET KYC BY ID
    // =========================================================

    @Operation(
            summary = "Get KYC by ID",
            description = "Returns the complete KYC record for the provided KYC ID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "KYC found",
                    content = @Content(
                            schema = @Schema(
                                    implementation = KycResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "KYC not found"
            )
    })
    @GetMapping("/{kycId}")
    public ResponseEntity<KycResponse> getKycById(

            @Parameter(
                    description = "Unique KYC ID",
                    example = "4"
            )
            @PathVariable
            Long kycId
    ) {

        KycResponse response =
                kycService.getKycById(kycId);

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // GET KYC BY CIF ID
    // =========================================================

    @Operation(
            summary = "Get KYC records by CIF ID",
            description = "Returns all KYC records associated with a given CIF ID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "KYC records returned successfully"
            )
    })
    @GetMapping
    public ResponseEntity<List<KycResponse>> getKycsByCifId(

            @Parameter(
                    description = "Customer CIF ID",
                    example = "1001"
            )
            @RequestParam
            Long cifId
    ) {

        List<KycResponse> response =
                kycService.getKycsByCifId(cifId);

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // UPLOAD MULTIPLE DOCUMENTS
    // =========================================================

    @Operation(
            summary = "Upload KYC documents",
            description = """
                    Uploads multiple documents for a KYC in one multipart request.

                    Each document type must correspond to the file at the same position.

                    Example:

                    documentTypes[0] = PAN
                    files[0] = pan.pdf

                    documentTypes[1] = AADHAAR
                    files[1] = aadhaar.png

                    Allowed document types:
                    PAN, AADHAAR, ADDRESS_PROOF, SALARY_PROOF

                    Allowed file formats:
                    PDF, PNG, JPEG
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Documents uploaded successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid document request, duplicate type, unsupported file or final KYC state"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "KYC not found"
            )
    })
    @PostMapping(
            value = "/{kycId}/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<List<KycDocumentResponse>> uploadDocuments(

            @Parameter(
                    description = "KYC ID",
                    example = "4"
            )
            @PathVariable
            Long kycId,

            @Parameter(
                    description = """
                            Document types corresponding to uploaded files.

                            Example values:
                            PAN, AADHAAR, ADDRESS_PROOF
                            """
            )
            @RequestParam("documentTypes")
            List<DocumentType> documentTypes,

            @Parameter(
                    description = "Files corresponding to documentTypes"
            )
            @RequestPart("files")
            List<MultipartFile> files
    ) {

        List<KycDocumentResponse> response =
                kycDocumentService.uploadDocuments(
                        kycId,
                        documentTypes,
                        files
                );

        return ResponseEntity
                .status(201)
                .body(response);
    }

    // =========================================================
    // GET DOCUMENT METADATA
    // =========================================================

    @Operation(
            summary = "Get KYC documents",
            description = """
                    Returns document metadata for all documents uploaded
                    against the specified KYC.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Documents returned successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "KYC not found"
            )
    })
    @GetMapping("/{kycId}/documents")
    public ResponseEntity<List<KycDocumentResponse>> getDocuments(

            @Parameter(
                    description = "KYC ID",
                    example = "4"
            )
            @PathVariable
            Long kycId
    ) {

        List<KycDocumentResponse> response =
                kycDocumentService.getDocuments(kycId);

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // DOWNLOAD DOCUMENT
    // =========================================================

    @Operation(
            summary = "Download KYC document",
            description = """
                    Downloads the actual document associated with
                    the specified KYC and document ID.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Document downloaded successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Document not found"
            )
    })
    @GetMapping("/{kycId}/documents/{documentId}")
    public ResponseEntity<ByteArrayResource> downloadDocument(

            @Parameter(
                    description = "KYC ID",
                    example = "4"
            )
            @PathVariable
            Long kycId,

            @Parameter(
                    description = "Document ID",
                    example = "6"
            )
            @PathVariable
            Long documentId
    ) {

        KycDocument document =
                kycDocumentService.getDocument(
                        kycId,
                        documentId
                );

        ByteArrayResource resource =
                new ByteArrayResource(
                        document.getDocumentContent()
                );

        MediaType mediaType =
                MediaType.parseMediaType(
                        document.getContentType()
                );

        ContentDisposition contentDisposition =
                ContentDisposition
                        .attachment()
                        .filename(
                                document.getOriginalFileName(),
                                StandardCharsets.UTF_8
                        )
                        .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(
                        document.getFileSizeBytes()
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition.toString()
                )
                .body(resource);
    }

    // =========================================================
    // VERIFY DOCUMENT
    // =========================================================

    @Operation(
            summary = "Verify KYC document",
            description = """
                    Verifies a KYC document.

                    Allowed verification results:

                    VERIFIED
                    MISMATCH

                    If the document is marked MISMATCH,
                    the parent KYC status becomes FLAGGED.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Document verification updated successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid verification status or KYC already finalized"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Document not found"
            )
    })
    @PatchMapping(
            "/{kycId}/documents/{documentId}/verification"
    )
    public ResponseEntity<KycDocumentResponse> verifyDocument(

            @Parameter(
                    description = "KYC ID",
                    example = "4"
            )
            @PathVariable
            Long kycId,

            @Parameter(
                    description = "Document ID",
                    example = "6"
            )
            @PathVariable
            Long documentId,

            @Valid
            @RequestBody
            DocumentVerificationRequest request
    ) {

        KycDocumentResponse response =
                kycDocumentService.verifyDocument(
                        kycId,
                        documentId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // FINAL KYC DECISION
    // =========================================================

    @Operation(
            summary = "Make final KYC decision",
            description = """
                    Makes the final KYC decision.

                    Supported decisions:

                    APPROVED
                    REJECTED

                    When the KYC is approved or rejected,
                    the KYC service attempts to send the final status
                    to the CIF service and Notification service.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Final KYC decision recorded successfully",
                    content = @Content(
                            schema = @Schema(
                                    implementation = KycResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid decision or KYC already finalized"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "KYC not found"
            )
    })
    @PatchMapping("/{kycId}/decision")
    public ResponseEntity<KycResponse> makeDecision(

            @Parameter(
                    description = "KYC ID",
                    example = "4"
            )
            @PathVariable
            Long kycId,

            @Valid
            @RequestBody
            KycDecisionRequest request
    ) {

        KycResponse response =
                kycService.makeDecision(
                        kycId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // RETRY CIF SYNC
    // =========================================================

    @Operation(
            summary = "Retry CIF synchronization",
            description = """
                    Retries synchronization of the final KYC status
                    with the CIF service.

                    This API should be used when the original CIF update
                    failed because the CIF service was unavailable.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "CIF synchronization attempted successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "KYC cannot be synchronized or maximum retry limit reached"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "KYC not found"
            )
    })
    @PostMapping("/{kycId}/sync")
    public ResponseEntity<KycResponse> retryCifSync(

            @Parameter(
                    description = "KYC ID",
                    example = "4"
            )
            @PathVariable
            Long kycId
    ) {

        KycResponse response =
                kycService.retryCifSync(kycId);

        return ResponseEntity.ok(response);
    }
}