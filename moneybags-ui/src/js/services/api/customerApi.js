define(['services/api/client', 'services/auth/session'], function (client, session) {
  'use strict';

  function uploadKycDocuments(kycId, documents) {
    var form = new FormData();
    (documents || []).forEach(function (document) {
      form.append('documentTypes', document.documentType);
      form.append('files', document.file);
    });
    return client.mutate('/api/v1/kycs/' + client.encode(kycId) + '/documents', 'POST', form, {
      idempotent: false
    });
  }

  return {
    registerCustomer: function (body) { return session.register(body); },
    createCustomerProfile: function (body) {
      return client.mutate('/api/v1/cifs', 'POST', body, { idempotent: false });
    },
    getCif: function (cifId) { return client.request('/api/v1/cifs/' + client.encode(cifId)); },
    getCustomerProfile: function (cifId) { return client.request('/api/v1/cifs/' + client.encode(cifId)); },
    updateCustomerProfile: function (cifId, body) {
      return client.mutate('/api/v1/cifs/' + client.encode(cifId), 'PUT', body, { idempotent: false });
    },
    listKycCases: function (cifId) { return client.request('/api/v1/kycs', { params: { cifId: cifId } }); },
    getKyc: function (kycId) { return client.request('/api/v1/kycs/' + client.encode(kycId)); },
    getKycDocuments: function (kycId) { return client.request('/api/v1/kycs/' + client.encode(kycId) + '/documents'); },
    listKycDocuments: function (kycId) { return client.request('/api/v1/kycs/' + client.encode(kycId) + '/documents'); },
    uploadKycDocuments: uploadKycDocuments,
    getKycQueue: function (filters) { return client.request('/api/v1/kycs/admin/work-queue', { params: filters }); },
    verifyKycDocument: function (kycId, documentId, body) {
      return client.mutate('/api/v1/kycs/' + client.encode(kycId) + '/documents/' + client.encode(documentId) + '/verification', 'PATCH', body, { idempotent: false });
    },
    decideKyc: function (kycId, body) {
      return client.mutate('/api/v1/kycs/' + client.encode(kycId) + '/decision', 'PATCH', body, { idempotent: false });
    }
  };
});
