package com.moneybags.cif.dto.response;

public record CustomerContactDetailsResponse(

        Long cifId,
        String firstName,
        String lastName,
        String email,
        String number,
        String address
) {
}