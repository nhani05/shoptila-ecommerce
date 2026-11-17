import axios from 'axios';

/**
 * Cổng gọi HTTP duy nhất của frontend.
 *
 * <p>JWT sẽ được gắn ở US-02. Không lưu token trong {@code localStorage}.</p>
 */
export const axiosClient = axios.create({
  baseURL: '/api/v1',
  timeout: 15_000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});
